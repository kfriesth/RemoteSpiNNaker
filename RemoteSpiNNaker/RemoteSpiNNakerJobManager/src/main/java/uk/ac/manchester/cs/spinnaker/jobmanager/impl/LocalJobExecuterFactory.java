package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import static java.io.File.createTempFile;
import static java.io.File.pathSeparatorChar;
import static org.apache.commons.io.FileUtils.copyToFile;
import static org.apache.commons.io.FileUtils.forceDeleteOnExit;
import static org.apache.commons.io.FileUtils.forceMkdirParent;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.ac.manchester.cs.spinnaker.job.JobManagerInterface.JOB_PROCESS_MANAGER_ZIP;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuter;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;

public class LocalJobExecuterFactory implements JobExecuterFactory {
	private static final String JOB_PROCESS_MANAGER_MAIN_CLASS = "uk.ac.manchester.cs.spinnaker.jobprocessmanager.JobProcessManager";

	private static File getJavaExec() throws IOException {
		File binDir = new File(System.getProperty("java.home"), "bin");
		File exec = new File(binDir, "java");
		if (!exec.canExecute())
			exec = new File(binDir, "java.exe");
		return exec;
	}

	private final boolean deleteOnExit;
	private final boolean liveUploadOutput;
	private final boolean requestSpiNNakerMachine;

	private List<File> jobProcessManagerClasspath = new ArrayList<>();
	private File jobExecuterDirectory = null;
	private static Logger logger = getLogger(Executer.class);
	private final ThreadGroup threadGroup;

	public LocalJobExecuterFactory(boolean deleteOnExit,
			boolean liveUploadOutput, boolean requestSpiNNakerMachine)
			throws IOException {
		this.deleteOnExit = deleteOnExit;
		this.liveUploadOutput = liveUploadOutput;
		this.requestSpiNNakerMachine = requestSpiNNakerMachine;
        this.threadGroup = new ThreadGroup("LocalJob");

		// Create a temporary folder
		jobExecuterDirectory = createTempFile("jobExecuter", "tmp");
		jobExecuterDirectory.delete();
		jobExecuterDirectory.mkdirs();
		jobExecuterDirectory.deleteOnExit();

		// Find the JobManager resource
		InputStream jobManagerStream = getClass().getResourceAsStream(
				"/" + JOB_PROCESS_MANAGER_ZIP);
		if (jobManagerStream == null)
			throw new UnsatisfiedLinkError("/" + JOB_PROCESS_MANAGER_ZIP
					+ " not found in classpath");

		// Extract the JobManager resources
		try (ZipInputStream input = new ZipInputStream(jobManagerStream)) {
			for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input
					.getNextEntry()) {
				if (entry.isDirectory())
					continue;
				File entryFile = new File(jobExecuterDirectory, entry.getName());
				forceMkdirParent(entryFile);
				copyToFile(input, entryFile);
				forceDeleteOnExit(entryFile);

				if (entryFile.getName().endsWith(".jar"))
					jobProcessManagerClasspath.add(entryFile);
			}
		}
	}

	@Override
	public JobExecuter createJobExecuter(JobManager manager, URL baseUrl)
			throws IOException {
		String uuid = UUID.randomUUID().toString();
		List<String> arguments = new ArrayList<>();
		arguments.add("--serverUrl");
		arguments.add(baseUrl.toString());
		arguments.add("--local");
		arguments.add("--executerId");
		arguments.add(uuid);
		if (deleteOnExit)
			arguments.add("--deleteOnExit");
		if (liveUploadOutput)
			arguments.add("--liveUploadOutput");
		if (requestSpiNNakerMachine)
			arguments.add("--requestMachine");

		return new Executer(manager, arguments, uuid);
	}

	class Executer implements JobExecuter,Runnable {
	    private final JobManager jobManager;
	    private final List<String> arguments;
	    private final String id;
	    private final File javaExec;

	    private File outputLog = createTempFile("exec", ".log");
	    private JobOutputPipe pipe;
	    private Process process;
	    private IOException startException;

	    /**
	    * Create a JobExecuter
	    * @param arguments The arguments to use
	    * @param id The id of the executer
	    * @throws IOException If there is an error creating the log file
	    */
		public Executer(JobManager jobManager, List<String> arguments, String id)
				throws IOException {
	        this.jobManager = jobManager;
	        this.arguments = arguments;
	        this.id = id;
	        javaExec = getJavaExec();
	    }

	    @Override
	    public String getExecuterId() {
	        return id;
	    }

	    @Override
	    public void startExecuter() {
	        new Thread(threadGroup, this, "Executer").start();
	    }

		/**
		 * Runs the external job
		 * 
		 * @throws IOException
		 *             If there is an error starting the job
		 */
	    @Override
		public void run() {
	        startSubprocess(constructArguments());

	        logger.debug("Waiting for process to finish");
			try {
				process.waitFor();
			} catch (InterruptedException e) {
				// Do Nothing
			}
			logger.debug("Process finished, closing pipe");
			pipe.close();

			reportResult();
		}

		private List<String> constructArguments() {
			List<String> command = new ArrayList<>();
	        command.add(javaExec.getAbsolutePath());

	        StringBuilder classPathBuilder = new StringBuilder();
	        for (File file : jobProcessManagerClasspath) {
	            if (classPathBuilder.length() != 0)
	                classPathBuilder.append(pathSeparatorChar);
	            classPathBuilder.append(file);
	        }
	        command.add("-cp");
	        command.add(classPathBuilder.toString());
	        logger.debug("Classpath: " + classPathBuilder);

	        command.add(JOB_PROCESS_MANAGER_MAIN_CLASS);
	        logger.debug("Main command: " + JOB_PROCESS_MANAGER_MAIN_CLASS);
	        for (String argument : arguments) {
	            command.add(argument);
	            logger.debug("Argument: " + argument);
	        }
			return command;
		}


		private void startSubprocess(List<String> command) {
			ProcessBuilder builder = new ProcessBuilder(command);
	        builder.directory(jobExecuterDirectory);
	        logger.debug("Working directory: " + jobExecuterDirectory);
	        builder.redirectErrorStream(true);
	        synchronized (this) {
	            try {
	                logger.debug("Starting execution process");
	                process = builder.start();
	                logger.debug("Starting pipe from process");
	                pipe = new JobOutputPipe(threadGroup, process.getInputStream(), outputLog);
	                pipe.start();
	            } catch (IOException e) {
	                logger.error("Error running external job", e);
	                startException = e;
	            }
	            notifyAll();
			}
		}

		private void reportResult() {
			StringBuilder logToAppend = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new FileReader(
					outputLog))) {
				while (true) {
					String line = reader.readLine();
					while (line == null)
						break;
					logToAppend.append(line).append("\n");
				}
			} catch (IOException e) {
				logger.warn("problem in reporting log", e);
			}
			jobManager.setExecutorExited(id, logToAppend.toString());
		}

		/**
		 * Gets an OutputStream which writes to the process stdin.
		 * 
		 * @return An OutputStream
		 */
		public OutputStream getProcessOutputStream() throws IOException {
			synchronized (this) {
				while ((process == null) && (startException == null)) {
					try {
						wait();
					} catch (InterruptedException e) {
						// Do Nothing
					}
				}
				if (startException != null)
					throw startException;
				return process.getOutputStream();
			}
		}

		/**
		 * Gets the location of the process log file
		 * 
		 * @return The location of the log file
		 */
		public File getLogFile() {
			return outputLog;
		}
	}
}
