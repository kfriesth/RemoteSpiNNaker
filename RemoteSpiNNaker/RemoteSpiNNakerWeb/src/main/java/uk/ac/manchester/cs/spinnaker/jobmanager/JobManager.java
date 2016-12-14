package uk.ac.manchester.cs.spinnaker.jobmanager;

import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.apache.commons.io.FileUtils.copyInputStreamToFile;
import static org.apache.commons.io.FileUtils.forceMkdirParent;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
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
	/** Requests close within this much of a board get an extra one */
	private static final double SLOP_FACTOR = 0.1;
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

	/**{@inheritDoc}*/
	@Override
	@Transactional
	public void addJob(Job job) throws IOException {
		requireNonNull(job);
		logger.info("New job " + job.getId());

		JobExecuter executer = jobExecuterFactory.createJobExecuter(baseUrl);
		storage.addJob(job, executer.getExecuterId());
		executer.startExecuter();
	}

	/**
	 * Get the job with the ID.
	 * 
	 * @param the
	 *            job ID to look up.
	 * @return the job, never <tt>null</tt>
	 * @throws WebApplicationException
	 *             if there's no such job.
	 */
	private Job findJob(int id) {
		Job job = storage.getJob(id);
		if (job == null)
			throw new WebApplicationException("bad job id", 400);
		return job;
	}

	/**{@inheritDoc}*/
	@Override
	@Transactional
	public Job getNextJob(String executerId) {
		requireNonNull(executerId);
		Job job = storage.getJob(executerId);
		if (job == null)
			throw new WebApplicationException("bad executer id", 400);
		logger.info("Executer " + executerId + " is running " + job.getId());
		queueManager.setJobRunning(job);
		storage.markRunning(job);
		return job;
	}

	/**{@inheritDoc}*/
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
		Job job = findJob(id);
		if (runTime < 0)
			throw new WebApplicationException("runTime must be specified and not negative", 400);

		logger.info("Request for " + nCores + " cores or " + nChips
				+ " chips or " + nBoards + " boards for " + (runTime / 1000.0)
				+ " seconds");

		int nBoardsToRequest;
		long quotaNCores;

		if (nBoards >= 0) {
			nBoardsToRequest = nBoards;
			quotaNCores = (long) (nBoards * CORES_PER_CHIP * CHIPS_PER_BOARD);
		} else if (nChips >= 0) {
			double nChipsExact = nChips;
			quotaNCores = (long) (nChips * CORES_PER_CHIP);
			nBoardsToRequest = (int) ceil(max(nChipsExact, 1.0) / CHIPS_PER_BOARD + SLOP_FACTOR);
		} else if (nCores >= 0) {
			double nChipsExact = nCores / CORES_PER_CHIP;
			quotaNCores = nCores;
			nBoardsToRequest = (int) ceil(max(nChipsExact, 1.0) / CHIPS_PER_BOARD + SLOP_FACTOR);
		} else {
			// If nothing specified, use 3 boards
			nBoardsToRequest = 3;
			quotaNCores = (long) (3 * CORES_PER_CHIP * CHIPS_PER_BOARD);
		}

		// Clamp values to sanity
		nBoardsToRequest = max(nBoardsToRequest, 1);
		quotaNCores = max(quotaNCores, 1);

		SpinnakerMachine machine = machineManager
				.getNextAvailableMachine(nBoardsToRequest);
		storage.addMachine(job, machine);
		logger.info("Running " + id + " on " + machine.getMachineName());
		long resourceUsage = (long) ((runTime / 1000.0) * quotaNCores);
		logger.info("Resource usage " + resourceUsage);
		storage.initResourceUsage(job, resourceUsage, (int) quotaNCores);

		return machine;
	}

	/**{@inheritDoc}*/
	@Override
	@Transactional
	public void extendJobMachineLease(int id, double runTime) {
		Job job = findJob(id);
		if (runTime < 0.0)
			// TODO handle the default a bit better
			return;
		// TODO Check quota that the lease can be extended

		storage.setResourceUsage(job, runTime / 1000.0);
	}

	/**{@inheritDoc}*/
	@Override
	@Transactional
	public JobMachineAllocated checkMachineLease(int id, int waitTime) {
		List<SpinnakerMachine> machines = storage.getMachines(findJob(id));

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

	/**{@inheritDoc}*/
	@Override
	@Transactional
	public void appendLog(int id, String logToAppend) {
		findJob(id);
		logger.debug("Updating log for " + id);
		logger.trace(id + ": " + logToAppend);
		queueManager.appendJobLog(id, requireNonNull(logToAppend));
	}

	private static String verifyFilename(String filename) {
		requireNonNull(filename);
		filename = filename.replaceFirst("^/+", "");
		for (String bit : filename.split("/"))
			if (bit.equals("/") || bit.equals(".") || bit.equals(".."))
				throw new WebApplicationException("bad filename", 400);
		return filename;
	}

	/**{@inheritDoc}*/
	@Override
	@Transactional
	public void addOutput(String projectId, int id, String output,
			InputStream input) {
		output = verifyFilename(output);
		requireNonNull(input);
		Job job = findJob(id);
		File tempOutputDir;
		try {
			tempOutputDir = storage.getTempDirectory(job);
		} catch (IOException e) {
			logger.error("Error creating temporary output directory for " + id,
					e);
			throw new WebApplicationException(INTERNAL_SERVER_ERROR);
		}

		File outputFile = new File(tempOutputDir, output);
		try {
			forceMkdirParent(outputFile);
			copyInputStreamToFile(input, outputFile);
		} catch (IOException e) {
			logger.error("Error writing file " + outputFile + " for job " + id,
					e);
			throw new WebApplicationException(INTERNAL_SERVER_ERROR);
		}
	}

	private List<DataItem> getOutputFiles(String projectId, Job job,
			String baseFile, List<String> outputs) throws IOException {
		int id = job.getId();
		List<DataItem> outputItems = new ArrayList<>();

		if (outputs != null && !outputs.isEmpty()) {
			List<File> outputFiles = new ArrayList<>();
			for (String filename : outputs)
				outputFiles.add(new File(verifyFilename(filename)));
			outputItems.addAll(outputManager.addOutputs(projectId, id,
					new File(baseFile), outputFiles));
		}

		File directory = storage.getTempDirectory(job);
		Collection<File> filelist = listFiles(directory, null, true);
		if (filelist != null && !filelist.isEmpty())
			outputItems.addAll(outputManager.addOutputs(projectId, id,
					directory, filelist));
		return outputItems;
	}

	/**{@inheritDoc}*/
	@Override
	@Transactional
	public void addProvenance(int id, String item, String value, String body) {
		if (value == null || value.isEmpty())
			value = body;
		storage.addProvenance(findJob(id), requireNonNull(item),
				requireNonNull(value));
	}

	/**{@inheritDoc}*/
	@Override
	@Transactional
	public void setJobFinished(String projectId, int id, String logToAppend,
			String baseDirectory, List<String> outputs) {
		requireNonNull(projectId);
		requireNonNull(logToAppend);
		requireNonNull(baseDirectory);
		requireNonNull(outputs);
		logger.debug("Marking job " + id + " as finished");
		Job job = findJob(id);
		releaseAllocatedMachines(job);

		// Do these before anything that can throw
		long resourceUsage = storage.getResourceUsage(job);
		Map<String, String> prov = storage.getProvenance(job);
		if (prov.isEmpty())
			prov = null;
		storage.markDone(job);

		try {
			queueManager.setJobFinished(job, logToAppend,
					getOutputFiles(projectId, job, baseDirectory, outputs),
					resourceUsage, prov);
		} catch (IOException e) {
			logger.error("Error creating URLs while updating job", e);
		}
	}

	/** @return <tt>true</tt> if there were machines removed by this. */
	private boolean releaseAllocatedMachines(Job job) {
		List<SpinnakerMachine> machines = storage.getMachines(job);
		for (SpinnakerMachine machine : machines)
			machineManager.releaseMachine(machine);
		return !machines.isEmpty();
	}

	/**{@inheritDoc}*/
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
		Job job = findJob(id);
		releaseAllocatedMachines(job);
		storage.markDone(job);

		Exception exception = reconstructRemoteException(error, stackTrace);
		// Do these before anything that can throw
		long resourceUsage = storage.getResourceUsage(job);
		Map<String, String> prov = storage.getProvenance(job);
		if (prov.isEmpty())
			prov = null;

		try {
			queueManager.setJobError(job, logToAppend,
					getOutputFiles(projectId, job, baseDirectory, outputs),
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

	/**
	 * @param executorId The ID of the executor that has executed.
	 * @param logToAppend May be <tt>null</tt>
	 */
	@Transactional
	public void setExecutorExited(String executorId, String logToAppend) {
		Job job = storage.getJob(requireNonNull(executorId));
		if (job != null) {
			storage.assignNewExecutor(job, null);
			int id = job.getId();
			logger.debug("Job " + id + " has exited");

			if (releaseAllocatedMachines(job)) {
				logger.debug("Job " + id + " has not exited cleanly");
				try {
					long resourceUsage = storage.getResourceUsage(job);
					Map<String, String> prov = storage.getProvenance(job);
					if (prov.isEmpty())
						prov = null;
					String projectId = new File(job.getCollabId()).getName();
					queueManager.setJobError(job, logToAppend,
							getOutputFiles(projectId, job, null, null),
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
			for (Job job : storage.getWaiting()) {
				JobExecuter executer = jobExecuterFactory
						.createJobExecuter(baseUrl);
				storage.assignNewExecutor(job, executer);
				executer.startExecuter();
			}
		} catch (IOException e) {
			logger.error("Could not launch a new executer", e);
		}
	}

	/**{@inheritDoc}*/
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
