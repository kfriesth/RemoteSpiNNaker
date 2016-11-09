package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import static java.io.File.createTempFile;
import static java.io.File.pathSeparatorChar;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuter;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;

/**
 * Executes jobs in an external process
 *
 */
public class LocalJobExecuter extends Thread implements JobExecuter {
    private final JobManager jobManager;
    private final File javaExec;
    private final List<File> classPath;
    private final String mainClass;
    private final List<String> arguments;
    private final String id;

    private File workingDirectory;
    private File outputLog = createTempFile("exec", ".log");
    private JobOutputPipe pipe;
    private Process process;
    private Logger logger = getLogger(getClass());
    private IOException startException;

    /**
    * Create a JobExecuter
    * @param javaExec The Java executable
    * @param classPath The classpath of the job
    * @param mainClass The main class to run
    * @param arguments The arguments to use
    * @param workingDirectory The working directory to run in
    * @param id The id of the executer
    * @throws IOException If there is an error creating the log file
    */
    public LocalJobExecuter(JobManager jobManager, File javaExec,
            List<File> classPath, String mainClass, List<String> arguments,
            File workingDirectory, String id) throws IOException {
        this.jobManager = jobManager;
        this.javaExec = javaExec;
        this.classPath = classPath;
        this.mainClass = mainClass;
        this.arguments = arguments;
        this.id = id;
    }

    @Override
    public String getExecuterId() {
        return id;
    }

    @Override
    public void startExecuter() {
        start();
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
        for (File file : classPath) {
            if (classPathBuilder.length() != 0)
                classPathBuilder.append(pathSeparatorChar);
            classPathBuilder.append(file);
        }
        command.add("-cp");
        command.add(classPathBuilder.toString());
        logger.debug("Classpath: " + classPathBuilder);

        command.add(mainClass);
        logger.debug("Main command: " + mainClass);
        for (String argument : arguments) {
            command.add(argument);
            logger.debug("Argument: " + argument);
        }
		return command;
	}


	private void startSubprocess(List<String> command) {
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
			e.printStackTrace();
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
