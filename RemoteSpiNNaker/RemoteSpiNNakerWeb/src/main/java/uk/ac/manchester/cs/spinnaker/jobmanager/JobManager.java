package uk.ac.manchester.cs.spinnaker.jobmanager;

import static java.io.File.createTempFile;
import static java.lang.Math.ceil;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.apache.commons.io.FileUtils.copyInputStreamToFile;
import static org.apache.commons.io.FileUtils.forceDelete;
import static org.apache.commons.io.FileUtils.forceMkdir;
import static org.apache.commons.io.FileUtils.forceMkdirParent;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.PostConstruct;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import uk.ac.manchester.cs.spinnaker.job.JobMachineAllocated;
import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTrace;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTraceElement;
import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.MachineManager;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueListener;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueManager;
import uk.ac.manchester.cs.spinnaker.rest.OutputManager;

/**
 * The manager of jobs; synchronises and manages all the ongoing and future
 * processes and machines.
 */
// TODO needs security; Role = JobEngine
public class JobManager implements NMPIQueueListener, JobManagerInterface {
	private static final double CHIPS_PER_BOARD = 48.0;
	private static final double CORES_PER_CHIP = 15.0;
	public static final String JOB_PROCESS_MANAGER_JAR = "RemoteSpiNNakerJobProcessManager.jar";

	@Autowired
	private MachineManager machineManager;
	@Autowired
	private NMPIQueueManager queueManager;
	@Autowired
	private OutputManager outputManager;
	private final URL baseUrl;
	@Autowired
	private JobExecuterFactory jobExecuterFactory;
    @Value("${restartJobExecutorOnFailure}")
	private boolean restartJobExecuterOnFailure;
    @Autowired
    private JobStorage storage;

	private Logger logger = getLogger(getClass());
	private Map<Integer, List<SpinnakerMachine>> allocatedMachines = new HashMap<>();
	private BlockingQueue<Job> jobsToRun = new LinkedBlockingQueue<>();
	private Map<String, JobExecuter> jobExecuters = new HashMap<>();
	private Map<String, Job> executorJobId = new HashMap<>();
	private Map<Integer, File> jobOutputTempFiles = new HashMap<>();
	private Map<Integer, Long> jobNCores = new HashMap<>();
	private Map<Integer, Long> jobResourceUsage = new HashMap<>();
	private Map<Integer, Map<String, String>> jobProvenance = new HashMap<>();
	private ThreadGroup threadGroup;

	public JobManager(URL baseUrl) {
		this.baseUrl = requireNonNull(baseUrl);
	}

	@PostConstruct
	void startManager() {
		threadGroup = new ThreadGroup("NMPI");
		// Start the queue manager
		queueManager.addListener(this);
		new Thread(threadGroup, queueManager, "QueueManager").start();
	}

	@Override
	@Transactional
	public void addJob(Job job) throws IOException {
		requireNonNull(job);
		logger.info("New job " + job.getId());

		// Add the job to the set of jobs to be run
		synchronized (jobExecuters) {
			jobsToRun.offer(job);//FIXME
			// Start an executer for the job
			JobExecuter executer = jobExecuterFactory.createJobExecuter(this,
					baseUrl);
			jobExecuters.put(executer.getExecuterId(), executer);
			storage.addJob(job, executer.getExecuterId());
			executer.startExecuter();
		}
	}

	/**
	 * You need to hold the lock on {@link #jobExecuters} when running this
	 * method.
	 * @return the ID of the executer
	 */
	private String launchExecuter() throws IOException {
		JobExecuter executer = jobExecuterFactory.createJobExecuter(this,
				baseUrl);
		jobExecuters.put(executer.getExecuterId(), executer);
		executer.startExecuter();
		return executer.getExecuterId();
	}

	@Override
	@Transactional
	public Job getNextJob(String executerId) {
		requireNonNull(executerId);
		Job job = storage.getJob(executerId);
		executorJobId.put(executerId, job);
		logger.info("Executer " + executerId + " is running " + job.getId());
		queueManager.setJobRunning(job.getId());
		storage.markRunning(job);
		return job;
	}

	@Override
	public SpinnakerMachine getLargestJobMachine(int id, double runTime) {
		// TODO Check quota to get the largest machine within the quota

		SpinnakerMachine largest = null;
		for (SpinnakerMachine machine : machineManager.getMachines())
			if (largest == null || machine.getArea() > largest.getArea())
				largest = machine;

		return largest;
	}

	/**{@inheritDoc}*/
	@Override
	@Transactional
	public SpinnakerMachine getJobMachine(int id, int nCores, int nChips,
			int nBoards, double runTime) {
		// TODO Check quota

		logger.info("Request for " + nCores + " cores or " + nChips
				+ " chips or " + nBoards + " boards for " + (runTime / 1000.0)
				+ " seconds");

		int nBoardsToRequest = nBoards;
		long quotaNCores = (long) (nBoards * CORES_PER_CHIP * CHIPS_PER_BOARD);

		// If nothing specified, use 3 boards
		if (nBoards <= 0 && nChips <= 0 && nCores <= 0) {
			nBoardsToRequest = 3;
			quotaNCores = (long) (3 * CORES_PER_CHIP * CHIPS_PER_BOARD);
		}

		// If boards not specified, use cores or chips
		if (nBoardsToRequest <= 0) {
			double nChipsExact = nChips;
			quotaNCores = (long) (nChipsExact * CORES_PER_CHIP);

			// If chips not specified, use cores
			if (nChipsExact <= 0) {
				nChipsExact = nCores / CORES_PER_CHIP;
				quotaNCores = nCores;
			}

			double nBoardsExact = (double) nChips / CHIPS_PER_BOARD;

			if (ceil(nBoardsExact) - nBoardsExact < 0.1)
				nBoardsExact += 1.0;
			if (nBoardsExact < 1.0)
				nBoardsExact = 1.0;
			nBoardsExact = ceil(nBoardsExact);
			nBoardsToRequest = (int) nBoardsExact;
		}

		SpinnakerMachine machine = allocateMachineForJob(id, nBoardsToRequest);
		logger.info("Running " + id + " on " + machine.getMachineName());
		long resourceUsage = (long) ((runTime / 1000.0) * quotaNCores);
		logger.info("Resource usage " + resourceUsage);
		synchronized (jobResourceUsage) {
			jobResourceUsage.put(id, resourceUsage);
			jobNCores.put(id, quotaNCores);
		}

		return machine;
	}

	/** Get a machine to run the job on */
	private SpinnakerMachine allocateMachineForJob(int id, int nBoardsToRequest) {
		SpinnakerMachine machine = machineManager
				.getNextAvailableMachine(nBoardsToRequest);
		synchronized (allocatedMachines) {
			if (!allocatedMachines.containsKey(id))
				allocatedMachines.put(id, new ArrayList<SpinnakerMachine>());
			allocatedMachines.get(id).add(machine);
		}
		return machine;
	}

	private List<SpinnakerMachine> getMachineForJob(int id) {
		synchronized (allocatedMachines) {
			return allocatedMachines.get(id);
		}
	}
	
	@Override
	@Transactional
	public void extendJobMachineLease(int id, double runTime) {
		if (runTime < 0.0)
			// TODO handle the default a bit better
			return;
		// TODO Check quota that the lease can be extended

		long usage;
		synchronized (jobResourceUsage) {
			usage = (long) (jobNCores.get(id) * (runTime / 1000.0));
			jobResourceUsage.put(id, usage);
		}
		logger.info("Usage for " + id + " now " + usage);
	}

	@Override
	@Transactional
	public JobMachineAllocated checkMachineLease(int id, int waitTime) {
		List<SpinnakerMachine> machines = getMachineForJob(id);

		// Return false if any machine is gone
		for (SpinnakerMachine machine : machines)
			if (!machineManager.isMachineAvailable(machine))
				return new JobMachineAllocated(false);

		// Wait for the state change of any machine
		waitForAnyMachineStateChange(waitTime, machines);

		// Again check for a machine which is gone
		for (SpinnakerMachine machine : machines)
			if (!machineManager.isMachineAvailable(machine))
				return new JobMachineAllocated(false);

		return new JobMachineAllocated(true);
	}

	private void waitForAnyMachineStateChange(final int waitTime,
			List<SpinnakerMachine> machines) {
		final BlockingQueue<Object> stateChangeSync = new LinkedBlockingQueue<>();
		for (final SpinnakerMachine machine : machines) {
			Thread stateThread = new Thread(threadGroup, new Runnable() {
				@Override
				public void run() {
					machineManager.waitForMachineStateChange(machine, waitTime);
					stateChangeSync.offer(this);
				}
			}, "waiting for " + machine);
			stateThread.setDaemon(true);
			stateThread.start();
		}
		try {
			stateChangeSync.take();
		} catch (InterruptedException e) {
			// Does Nothing
		}
	}

	@Override
	@Transactional
	public void appendLog(int id, String logToAppend) {
		logger.debug("Updating log for " + id);
		logger.trace(id + ": " + logToAppend);
		queueManager.appendJobLog(id, requireNonNull(logToAppend));
	}

	@Override
	@Transactional
	public void addOutput(String projectId, int id, String output,
			InputStream input) {
		requireNonNull(output);
		requireNonNull(input);
		try {
			if (!jobOutputTempFiles.containsKey(id)) {
				File tempOutputDir = createTempFile("jobOutput", ".tmp");
				forceDelete(tempOutputDir);
				forceMkdir(tempOutputDir);
				jobOutputTempFiles.put(id, tempOutputDir);
			}
		} catch (IOException e) {
			logger.error("Error creating temporary output directory for " + id,
					e);
			throw new WebApplicationException(INTERNAL_SERVER_ERROR);
		}

		File outputFile = new File(jobOutputTempFiles.get(id), output);
		try {
			forceMkdirParent(outputFile);
			copyInputStreamToFile(input, outputFile);
		} catch (IOException e) {
			logger.error("Error writing file " + outputFile + " for job " + id,
					e);
			throw new WebApplicationException(INTERNAL_SERVER_ERROR);
		}
	}

	private List<DataItem> getOutputFiles(String projectId, int id,
			String baseFile, List<String> outputs) throws IOException {
		List<DataItem> outputItems = new ArrayList<>();
		if (outputs != null) {
			List<File> outputFiles = new ArrayList<>();
			for (String filename : outputs)
				outputFiles.add(new File(filename));
			outputItems.addAll(outputManager.addOutputs(projectId, id,
					new File(baseFile), outputFiles));
		}
		if (jobOutputTempFiles.containsKey(id)) {
			File directory = jobOutputTempFiles.get(id);
			outputItems.addAll(outputManager.addOutputs(projectId, id,
					directory, listFiles(directory, null, true)));
		}
		return outputItems;
	}

	@Override
	@Transactional
	public void addProvenance(int id, String item, String value) {
		synchronized (jobProvenance) {
			if (!jobProvenance.containsKey(id))
				jobProvenance.put(id, new HashMap<String, String>());
			jobProvenance.get(id).put(requireNonNull(item),
					requireNonNull(value));
		}
	}

	private Map<String, String> getProvenance(int id) {
		Map<String, String> prov;
		synchronized (jobProvenance) {
			prov = jobProvenance.remove(id);
		}
		if (prov != null && prov.isEmpty())
			prov = null;
		return prov;
	}

	private long getResourceUsage(int id) {
		long resourceUsage = 0;
		synchronized (jobResourceUsage) {
			Long ru = jobResourceUsage.remove(id);
			if (ru != null) {
				resourceUsage = ru;
				jobNCores.remove(id);
			}
		}
		return resourceUsage;
	}

	@Override
	@Transactional
	public void setJobFinished(String projectId, int id, String logToAppend,
			String baseDirectory, List<String> outputs) {
		requireNonNull(projectId);
		requireNonNull(logToAppend);
		requireNonNull(baseDirectory);
		requireNonNull(outputs);
		logger.debug("Marking job " + id + " as finished");
		releaseAllocatedMachines(id);

		// Do these before anything that can throw
		long resourceUsage = getResourceUsage(id);
		Map<String, String> prov = getProvenance(id);

		try {
			queueManager.setJobFinished(id, logToAppend,
					getOutputFiles(projectId, id, baseDirectory, outputs),
					resourceUsage, prov);
		} catch (IOException e) {
			logger.error("Error creating URLs while updating job", e);
		}
	}

	/** @return <tt>true</tt> if there were machines removed by this. */
	private boolean releaseAllocatedMachines(int id) {
		synchronized (allocatedMachines) {
			List<SpinnakerMachine> machines = allocatedMachines.remove(id);
			if (machines != null)
				for (SpinnakerMachine machine : machines)
					machineManager.releaseMachine(machine);
			return machines != null;
		}
	}

	@Override
	@Transactional
	public void setJobError(String projectId, int id, String error,
			String logToAppend, String baseDirectory, List<String> outputs,
			RemoteStackTrace stackTrace) {
		requireNonNull(projectId);
		requireNonNull(error);
		requireNonNull(logToAppend);
		requireNonNull(baseDirectory);
		requireNonNull(outputs);
		requireNonNull(stackTrace);

		logger.debug("Marking job " + id + " as error");
		releaseAllocatedMachines(id);

		Exception exception = reconstructRemoteException(error, stackTrace);
		// Do these before anything that can throw
		long resourceUsage = getResourceUsage(id);
		Map<String, String> prov = getProvenance(id);

		try {
			queueManager.setJobError(id, logToAppend,
					getOutputFiles(projectId, id, baseDirectory, outputs),
					exception, resourceUsage, prov);
		} catch (IOException e) {
			logger.error("Error creating URLs while updating job", e);
		}
	}

	private static final StackTraceElement[] STE_TMPL = new StackTraceElement[0];

	private Exception reconstructRemoteException(String error,
			RemoteStackTrace stackTrace) {
		ArrayList<StackTraceElement> elements = new ArrayList<>();
		for (RemoteStackTraceElement element : stackTrace.getElements())
			elements.add(element.toSTE());

		Exception exception = new Exception(error);
		exception.setStackTrace(elements.toArray(STE_TMPL));
		return exception;
	}

	public void setExecutorExited(String executorId, String logToAppend) {
		requireNonNull(logToAppend);
		Job job = executorJobId.remove(requireNonNull(executorId));
		synchronized (jobExecuters) {
			jobExecuters.remove(executorId);
		}
		if (job != null) {
			int id = job.getId();
			logger.debug("Job " + id + " has exited");

			if (releaseAllocatedMachines(id)) {
				logger.debug("Job " + id + " has not exited cleanly");
				try {
					long resourceUsage = getResourceUsage(id);
					Map<String, String> prov = getProvenance(id);
					String projectId = new File(job.getCollabId()).getName();
					queueManager.setJobError(id, logToAppend,
							getOutputFiles(projectId, id, null, null),
							new Exception("Job did not finish cleanly"),
							resourceUsage, prov);
				} catch (IOException e) {
					logger.error("Error creating URLs while updating job", e);
				}
			}
		} else {
			logger.error("An executer has exited.  This could indicate an error!");
			logger.error(logToAppend);

			if (restartJobExecuterOnFailure)
				restartExecuters();
		}
	}

	private void restartExecuters() {
		try {
			int jobSize;
			synchronized (jobsToRun) {
				jobSize = jobsToRun.size();
			}
			synchronized (jobExecuters) {
				while (jobSize > jobExecuters.size())
					launchExecuter();
			}
		} catch (IOException e) {
			logger.error("Could not launch a new executer", e);
		}
	}

	@Override
	public Response getJobProcessManager() {
		InputStream jobManagerStream = getClass().getResourceAsStream(
				"/" + JOB_PROCESS_MANAGER_ZIP);
		if (jobManagerStream == null)
			throw new UnsatisfiedLinkError(JOB_PROCESS_MANAGER_ZIP
					+ " not found in classpath");
		return Response.ok(jobManagerStream).type(APPLICATION_ZIP).build();
	}
}
