package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes jobs in an external process
 *
 */
public class JobExecuter {

	private File javaExec = null;

	private List<File> classPath = null;

	private String mainClass = null;

	private List<String> arguments = null;

	private File workingDirectory = null;

	private File outputLog = File.createTempFile("exec", ".log");

	private Process process = null;

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
	public void run() throws IOException {
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

		command.add(mainClass);
		for (String argument : arguments) {
			command.add(argument);
		}

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(workingDirectory);
		builder.redirectErrorStream(true);
		builder.redirectOutput(outputLog);

		process = builder.start();
	}

	/**
	 * Gets an OutputStream which writes to the process stdin
	 * @return An OutputStream
	 */
	public OutputStream getProcessOutputStream() {
		return process.getOutputStream();
	}

	/**
	 * Gets the location of the process log file
	 * @return The location of the log file
	 */
	public File getLogFile() {
		return outputLog;
	}
}
