package uk.ac.manchester.cs.spinnaker.jobprocess.impl;

import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static uk.ac.manchester.cs.spinnaker.job.Status.Error;
import static uk.ac.manchester.cs.spinnaker.job.Status.Finished;
import static uk.ac.manchester.cs.spinnaker.job.Status.Running;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.ini4j.ConfigParser;

import uk.ac.manchester.cs.spinnaker.job.Status;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcess;
import uk.ac.manchester.cs.spinnaker.jobprocess.LogWriter;
import uk.ac.manchester.cs.spinnaker.jobprocess.ReaderLogWriter;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

/**
 * A process for running PyNN jobs
 */
public class PyNNJobProcess implements JobProcess<PyNNJobParameters> {
	private static final String SUBPROCESS_RUNNER = "python";
	private static final int FINALIZATION_DELAY = 1000;
	private static final Set<String> IGNORED_EXTENSIONS = new HashSet<>();
	private static final Set<String> IGNORED_DIRECTORIES = new HashSet<>();
	private static final Pattern ARGUMENT_FINDER = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");
	static {
		IGNORED_EXTENSIONS.add("pyc");
		IGNORED_DIRECTORIES.add("application_generated_data_files");
		IGNORED_DIRECTORIES.add("reports");
	};

	private File workingDirectory = null;
	private Status status = null;
	private Throwable error = null;
	private List<File> outputs = new ArrayList<>();

	private static Set<File> gatherFiles(File directory) {
		return new LinkedHashSet<>(listFiles(directory,
				TrueFileFilter.INSTANCE, new AbstractFileFilter() {
					@Override
					public boolean accept(File file) {
						return !IGNORED_DIRECTORIES.contains(file.getName());
					}
				}));
	}

	private boolean isIgnored(File file) {
		return IGNORED_EXTENSIONS.contains(getExtension(file.getName()));
	}

	private void log(String message) {
		System.err.println(message);
	}

	@Override
	public void execute(String machineUrl, SpinnakerMachine machine,
			PyNNJobParameters parameters, LogWriter logWriter) {
		try {
			status = Running;
			workingDirectory = new File(parameters.getWorkingDirectory());

			// TODO: Deal with hardware configuration
			File cfgFile = new File(workingDirectory, "spynnaker.cfg");

			// Add the details of the machine
			ConfigParser parser = new ConfigParser();
			if (cfgFile.exists())
				parser.read(cfgFile);

			if (!parser.hasSection("Machine"))
				parser.addSection("Machine");
			if (machine != null) {
				parser.set("Machine", "machineName", machine.getMachineName());
				parser.set("Machine", "version", machine.getVersion());
				parser.set("Machine", "width", machine.getWidth());
				parser.set("Machine", "height", machine.getHeight());
				String bmpDetails = machine.getBmpDetails();
				if (bmpDetails != null)
					parser.set("Machine", "bmp_names", bmpDetails);
			} else {
				parser.set("Machine", "remote_spinnaker_url", machineUrl);
			}
			parser.write(cfgFile);

			// Keep existing files to compare to later
			Set<File> existingFiles = gatherFiles(workingDirectory);

			// Execute the program
			int exitValue = runSubprocess(parameters, logWriter);

			// Get any output files
			Set<File> allFiles = gatherFiles(workingDirectory);
			for (File file : allFiles)
				if (!existingFiles.contains(file) && !isIgnored(file))
					outputs.add(file);

			// If the exit is an error, mark an error
			if (exitValue > 127)
				// Useful to distinguish this case
				throw new Exception("Python exited with signal ("
						+ (exitValue - 128) + ")");
			if (exitValue != 0)
				throw new Exception("Python exited with a non-zero code ("
						+ exitValue + ")");
			status = Finished;
		} catch (Throwable e) {
			error = e;
			status = Error;
		}
	}

	/** How to actually run a subprocess. */
	private int runSubprocess(PyNNJobParameters parameters, LogWriter logWriter)
			throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add(SUBPROCESS_RUNNER);

		Matcher scriptMatcher = ARGUMENT_FINDER.matcher(parameters.getScript());
		while (scriptMatcher.find())
			command.add(scriptMatcher.group(1).replace("{system}", "spiNNaker"));

		ProcessBuilder builder = new ProcessBuilder(command);
		log("Running " + command + " in " + workingDirectory);
		builder.directory(workingDirectory);
		builder.redirectErrorStream(true);
		Process process = builder.start();

		// Run a thread to gather the log
		try (ReaderLogWriter logger = new ReaderLogWriter(
				process.getInputStream(), logWriter)) {
			logger.start();

			// Wait for the process to finish
			int exitValue = process.waitFor();
			Thread.sleep(FINALIZATION_DELAY);
			return exitValue;
		}
	}

	@Override
	public Status getStatus() {
		return status;
	}

	@Override
	public Throwable getError() {
		return error;
	}

	@Override
	public List<File> getOutputs() {
		return outputs;
	}

	@Override
	public void cleanup() {
		// Does Nothing
	}
}
