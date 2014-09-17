package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Executes jobs in an external process
 *
 */
public class JobExecuter extends Thread {

	private File javaExec = null;

	private List<File> classPath = null;

	private String mainClass = null;

	private List<String> arguments = null;

	private File workingDirectory = null;

	private File outputLog = File.createTempFile("exec", ".log");

	private JobOutputPipe pipe = null;

	private Process process = null;

	private Log logger = LogFactory.getLog(getClass());

	private IOException startException = null;

	/**
	 * Create a JobExecuter
	 * @param javaExec The Java executable
	 * @param classPath The classpath of the job
	 * @param mainClass The main class to run
	 * @param arguments The arguments to use
	 * @param workingDirectory The working directory to run in
	 * @throws IOException If there is an error creating the log file
	 */
	public JobExecuter(File javaExec, List<File> classPath, String mainClass,
			List<String> arguments, File workingDirectory)
			throws IOException {
		this.javaExec = javaExec;
		this.classPath = classPath;
		this.mainClass = mainClass;
		this.arguments = arguments;
		this.workingDirectory = workingDirectory;
	}

	/**
	 * Runs the external job
	 * @throws IOException If there is an error starting the job
	 */
	public void run() {
		List<String> command = new ArrayList<String>();
		command.add(javaExec.getAbsolutePath());

		StringBuilder classPathBuilder = new StringBuilder();
		for (File file : classPath) {
			if (classPathBuilder.length() != 0) {
				classPathBuilder.append(File.pathSeparatorChar);
			}
			classPathBuilder.append(file);
		}
		command.add("-cp");
		command.add(classPathBuilder.toString());
		logger.debug("Classpath: " + classPathBuilder.toString());

		command.add(mainClass);
		logger.debug("Main command: " + mainClass);
		for (String argument : arguments) {
			command.add(argument);
			logger.debug("Argument: " + argument);
		}

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(workingDirectory);
		logger.debug("Working directory: " + workingDirectory);
		builder.redirectErrorStream(true);
		synchronized (this) {
			try {
			    logger.debug("Starting execution process");
			    process = builder.start();
			    logger.debug("Starting pipe from process");
				pipe = new JobOutputPipe(process.getInputStream(), outputLog);
				pipe.start();
				notifyAll();
			} catch (IOException e) {
				logger.error("Error running external job", e);
				startException = e;
				notifyAll();
			}
		}

		try {
			logger.debug("Waiting for process to finish");
			process.waitFor();
		} catch (InterruptedException e) {
			// Do Nothing
		}
		logger.debug("Process finished, closing pipe");
	    pipe.close();
	}

	/**
	 * Gets an OutputStream which writes to the process stdin
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
			if (startException != null) {
				throw startException;
			}
		    return process.getOutputStream();
		}
	}

	/**
	 * Gets the location of the process log file
	 * @return The location of the log file
	 */
	public File getLogFile() {
		return outputLog;
	}
}
