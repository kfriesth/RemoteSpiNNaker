package uk.ac.manchester.cs.spinnaker.jobprocessmanager;

import static java.lang.String.format;
import static java.lang.System.exit;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.eclipse.jgit.util.FileUtils.createTempDir;
import static uk.ac.manchester.cs.spinnaker.jobprocessmanager.RemoteSpiNNakerAPI.createJobManager;
import static uk.ac.manchester.cs.spinnaker.utils.FileDownloader.downloadFile;
import static uk.ac.manchester.cs.spinnaker.utils.Log.log;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Timer;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTrace;
import uk.ac.manchester.cs.spinnaker.job.Status;
import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job.pynn.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcess;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcessFactory;
import uk.ac.manchester.cs.spinnaker.jobprocess.LogWriter;
import uk.ac.manchester.cs.spinnaker.jobprocess.PyNNJobProcess;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

/**
 * Manages a running job process. This is run as a separate process from the
 * command line, and it assumes input is passed via {@link System#in}.
 */
public class JobProcessManager {
	private static final int UPDATE_INTERVAL = 500;

	/** The factory for converting parameters into processes. */
	private static final JobProcessFactory jobProcessFactory = new JobProcessFactory(
			"JobProcess");
	static {
		jobProcessFactory.addMapping(PyNNJobParameters.class,
				PyNNJobProcess.class);
	}

	class UploadingJobManagerLogWriter extends JobManagerLogWriter {
		private final Timer sendTimer;

		public UploadingJobManagerLogWriter() {
			sendTimer = new Timer(UPDATE_INTERVAL, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					sendLog();
				}
			});
		}

		private void sendLog() {
			String toWrite = null;
			synchronized (this) {
				if (isPopulated()) {
					toWrite = cached.toString();
					cached.setLength(0);
				}
			}
			if (toWrite != null) {
				log("Sending cached data to job manager");
				jobManager.appendLog(job.getId(), toWrite);
			}
		}

		@Override
		public void append(String logMsg) {
			log("Process Output: " + logMsg);
			synchronized (this) {
				cached.append(logMsg);
				sendTimer.restart();
			}
		}

		@Override
		public void stop() {
			sendTimer.stop();
		}
	}

	private final String serverUrl;
	private final boolean deleteOnExit;
	private final boolean isLocal;
	private final String executerId;
	private final boolean liveUploadOutput;
	private final boolean requestMachine;
	private final String authToken;

	private JobManagerInterface jobManager;
	private JobManagerLogWriter logWriter;
	private Job job;
	private String projectId;

	public JobProcessManager(String serverUrl, boolean deleteOnExit,
			boolean isLocal, String executerId, boolean liveUploadOutput,
			boolean requestMachine, String authToken) {
		this.serverUrl = requireNonNull(serverUrl,
				"--serverUrl must be specified");
		this.executerId = requireNonNull(executerId,
				"--executerId must be specified");
		this.deleteOnExit = deleteOnExit;
		this.isLocal = isLocal;
		this.liveUploadOutput = liveUploadOutput;
		this.requestMachine = requestMachine;
		this.authToken = authToken;
	}

	public void runJob() {
		try {
			jobManager = createJobManager(serverUrl, authToken);

			// Read the job
			job = jobManager.getNextJob(executerId);
			projectId = new File(job.getCollabId()).getName();

			// Create a temporary location for the job
			File workingDirectory = createTempDir("job", ".tmp", null);

			JobParameters parameters = getJobParameters(workingDirectory);

			// Create a process to process the request
			log("Creating process from parameters");
			JobProcess<JobParameters> process = jobProcessFactory
					.createProcess(parameters);
			logWriter = getLogWriter();

			// Read the machine
			Machine machine = getMachine();

			// Execute the process
			log("Running job " + job.getId() + " on " + machine + " using "
					+ parameters.getClass() + " reporting to " + serverUrl);
			process.execute(machine.url, machine.machine, parameters, logWriter);
			logWriter.stop();

			// Get the exit status
			processOutcome(workingDirectory, process, logWriter.getLog());
		} catch (Exception error) {
			reportFailure(error);
			exit(1);
		}
	}

	private void reportFailure(Throwable error) {
		if (jobManager == null || job == null) {
			log(error);
			return;
		}

		try {
			String log = "";
			if (logWriter != null) {
				logWriter.stop();
				log = logWriter.getLog();
			}

			jobManager.setJobError(projectId, job.getId(), error.getMessage(),
					log, "", new ArrayList<String>(), new RemoteStackTrace(
							error));
		} catch (Throwable t) {
			// Exception while reporting exception...
			log(t);
			log(error);
			exit(2);
		}
	}

	public static void main(String[] args) throws Exception {
		String serverUrl = null;
		boolean deleteOnExit = false;
		boolean isLocal = false;
		String executerId = null;
		boolean liveUploadOutput = false;
		boolean requestMachine = false;
		String authToken = null;

		for (int i = 0; i < args.length; i++)
			switch (args[i]) {
			case "--serverUrl":
				serverUrl = args[++i];
				break;
			case "--executerId":
				executerId = args[++i];
				break;
			case "--deleteOnExit":
				deleteOnExit = true;
				break;
			case "--local":
				isLocal = true;
				break;
			case "--liveUploadOutput":
				liveUploadOutput = true;
				break;
			case "--requestMachine":
				requestMachine = true;
				break;
			case "--authToken":
				try (BufferedReader r = new BufferedReader(
						new InputStreamReader(System.in))) {
					authToken = r.readLine();
				}
				break;
			default:
				throw new IllegalArgumentException("unknown option: " + args[i]);
			}

		new JobProcessManager(serverUrl, deleteOnExit, isLocal, executerId,
				liveUploadOutput, requestMachine, authToken).runJob();
		exit(0);
	}

	private static final int DEFAULT = -1;

	private Machine getMachine() {
		// (get a 3 board machine just now)
		if (requestMachine)
			return new Machine(jobManager.getJobMachine(job.getId(), DEFAULT,
					DEFAULT, DEFAULT, DEFAULT));
		return new Machine(serverUrl, job.getId());
	}

	/**
	 * Sort out the parameters to a job. Includes downloading any necessary
	 * files.
	 * 
	 * @param workingDirectory
	 *            The working directory for the job, used to write files.
	 * @return Description of the parameters.
	 * @throws IOException
	 *             If anything goes wrong, such as the parameters being
	 *             unreadable or the job being unsupported on the current
	 *             architectural configuration.
	 */
	private JobParameters getJobParameters(File workingDirectory)
			throws IOException {
		Map<String, JobParametersFactoryException> errors = new HashMap<>();
		JobParameters parameters = JobParametersFactory.getJobParameters(job,
				workingDirectory, errors);

		if (parameters == null) {
			if (!errors.isEmpty())
				throw new JobErrorsException(errors);
			// Miscellaneous other error
			throw new IOException(
					"The job did not appear to be supported on this system");
		}

		// Get any requested input files
		if (job.getInputData() != null)
			for (DataItem input : job.getInputData())
				downloadFile(input.getUrl(), workingDirectory, null);

		return parameters;
	}

	private JobManagerLogWriter getLogWriter() {
		if (!liveUploadOutput)
			return new SimpleJobManagerLogWriter();
		return new UploadingJobManagerLogWriter();
	}

	private void processOutcome(File workingDirectory, JobProcess<?> process,
			String log) throws IOException, FileNotFoundException {
		Status status = process.getStatus();
		log("Process has finished with status " + status);

		List<File> outputs = process.getOutputs();
		List<String> outputsAsStrings = new ArrayList<>();
		for (File output : outputs)
			if (isLocal)
				outputsAsStrings.add(output.getAbsolutePath());
			else
				try (InputStream input = new FileInputStream(output)) {
					jobManager.addOutput(projectId, job.getId(),
							output.getName(), input);
				}

		switch (status) {
		case Error:
			Throwable error = process.getError();
			jobManager.setJobError(projectId, job.getId(), error.getMessage(),
					log, workingDirectory.getAbsolutePath(), outputsAsStrings,
					new RemoteStackTrace(error));
			break;
		case Finished:
			jobManager.setJobFinished(projectId, job.getId(), log,
					workingDirectory.getAbsolutePath(), outputsAsStrings);

			// Clean up
			process.cleanup();
			if (deleteOnExit)
				deleteQuietly(workingDirectory);
			break;
		default:
			throw new IllegalStateException("Unknown status returned!");
		}
	}
}

class Machine {
	SpinnakerMachine machine;
	String url;

	Machine(SpinnakerMachine machine) {
		this.machine = machine;
	}

	Machine(String baseUrl, int id) {
		this.url = format("%sjob/%d/machine", baseUrl, id);
	}

	@Override
	public String toString() {
		if (machine != null)
			return machine.toString();
		return url;
	}
}

abstract class JobManagerLogWriter implements LogWriter {
	protected final StringBuilder cached = new StringBuilder();

	protected boolean isPopulated() {
		return cached.length() > 0;
	}

	public synchronized String getLog() {
		return cached.toString();
	}

	void stop() {
	}
}

class SimpleJobManagerLogWriter extends JobManagerLogWriter {
	@Override
	public void append(String logMsg) {
		log("Process Output: " + logMsg);
		synchronized (this) {
			cached.append(logMsg);
		}
	}
}

@SuppressWarnings("serial")
class JobErrorsException extends IOException {
	private static final String MAIN_MSG = "The job type was recognised"
			+ " by at least one factory, but could not be decoded.  The"
			+ " errors  are as follows:\n";

	private static String buildMessage(Map<String, ? extends Exception> errors) {
		StringBuilder problemBuilder = new StringBuilder(MAIN_MSG);
		for (String key : errors.keySet())
			problemBuilder.append(key).append(": ")
					.append(errors.get(key).getMessage()).append('\n');
		return problemBuilder.toString();
	}

	JobErrorsException(Map<String, JobParametersFactoryException> errors) {
		super(buildMessage(errors));
	}
}
