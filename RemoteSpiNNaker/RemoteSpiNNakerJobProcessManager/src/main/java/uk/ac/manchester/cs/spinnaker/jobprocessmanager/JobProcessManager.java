package uk.ac.manchester.cs.spinnaker.jobprocessmanager;

import static java.lang.String.format;
import static java.lang.System.exit;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.eclipse.jgit.util.FileUtils.createTempDir;
import static org.jboss.resteasy.util.Base64.encodeBytes;
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Timer;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTrace;
import uk.ac.manchester.cs.spinnaker.job.Status;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job_parameters.DirectPyNNJobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.GitPyNNJobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.job_parameters.UnsupportedJobException;
import uk.ac.manchester.cs.spinnaker.job_parameters.ZipPyNNJobParametersFactory;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcess;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcessFactory;
import uk.ac.manchester.cs.spinnaker.jobprocess.LogWriter;
import uk.ac.manchester.cs.spinnaker.jobprocess.PyNNJobProcess;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

/**
 * Manages a running job process. This is run as a separate process from the
 * command line, and it assumes input is passed via {@link System#in}.
 */
public class JobProcessManager {
	/** The factories for converting jobs into parameters. */
	private static final JobParametersFactory[] JOB_PARAMETER_FACTORIES = new JobParametersFactory[] {
			new GitPyNNJobParametersFactory(),
			new ZipPyNNJobParametersFactory(),
			new DirectPyNNJobParametersFactory() };

	/** The factory for converting parameters into processes. */
	private static final JobProcessFactory jobProcessFactory = new JobProcessFactory(
			"JobProcess");
	private static final Charset UTF8 = Charset.forName("UTF-8");
	static {
		jobProcessFactory.addMapping(PyNNJobParameters.class,
				PyNNJobProcess.class);
	}

	/** How to talk to the main website. */
	private static JobManagerInterface createJobManager(String url,
			final String authToken) {
		ResteasyClientBuilder builder = new ResteasyClientBuilder();
		// TODO Add https trust store, etc.
		ResteasyClient client = builder.build();
		client.register(new JacksonJsonProvider());
		if (authToken != null)
			client.register(new ClientRequestFilter() {
				@Override
				public void filter(ClientRequestContext requestContext)
						throws IOException {
					requestContext.getHeaders().add(AUTHORIZATION,
							encodeBytes(authToken.getBytes(UTF8)));
				}
			});
		return client.target(url).proxy(JobManagerInterface.class);
	}

	abstract static class JobManagerLogWriter implements LogWriter {
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

	static class SimpleJobManagerLogWriter extends JobManagerLogWriter {
		@Override
		public void append(String logMsg) {
			log("Process Output: " + logMsg);
			synchronized (this) {
				cached.append(logMsg);
			}
		}
	}

	private static final int UPDATE_INTERVAL = 500;

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

	JobProcessManager(String serverUrl, boolean deleteOnExit, boolean isLocal,
			String executerId, boolean liveUploadOutput,
			boolean requestMachine, String authToken) throws IOException {
		this.serverUrl = serverUrl;
		this.deleteOnExit = deleteOnExit;
		this.isLocal = isLocal;
		this.executerId = executerId;
		this.liveUploadOutput = liveUploadOutput;
		this.requestMachine = requestMachine;
		this.authToken = authToken;

		if (serverUrl == null)
			throw new IOException("--serverUrl must be specified");
		else if (executerId == null)
			throw new IOException("--executerId must be specified");
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
		
		new JobProcessManager(serverUrl, deleteOnExit, isLocal,
				executerId, liveUploadOutput, requestMachine, authToken).runJob();
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
		JobParameters parameters = null;
		Map<String, JobParametersFactoryException> errors = new HashMap<>();

		for (JobParametersFactory factory : JOB_PARAMETER_FACTORIES)
			try {
				parameters = factory.getJobParameters(job, workingDirectory);
				if (parameters != null)
					break;
			} catch (UnsupportedJobException e) {
				// Do Nothing
			} catch (JobParametersFactoryException e) {
				errors.put(factory.getClass().getSimpleName(), e);
			}

		if (parameters != null) {
			// Get any requested input files
			if (job.getInputData() != null)
				for (DataItem input : job.getInputData())
					downloadFile(input.getUrl(), workingDirectory, null);

			return parameters;
		}

		if (!errors.isEmpty()) {
			StringBuilder problemBuilder = new StringBuilder();
			problemBuilder.append("The job type was recognised by at least"
					+ " one factory, but could not be decoded.  The errors "
					+ " are as follows:\n");
			for (String key : errors.keySet())
				problemBuilder.append(key).append(": ")
						.append(errors.get(key).getMessage()).append('\n');
			throw new IOException(problemBuilder.toString());
		}

		// Miscellaneous other error
		throw new IOException(
				"The job did not appear to be supported on this system");
	}

	private JobManagerLogWriter getLogWriter() {
		if (!liveUploadOutput)
			return new SimpleJobManagerLogWriter();
		return new UploadingJobManagerLogWriter();
	}

	private void processOutcome(File workingDirectory,
			JobProcess<JobParameters> process, String log) throws IOException,
			FileNotFoundException {
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
			throw new RuntimeException("Unknown status returned!");
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
