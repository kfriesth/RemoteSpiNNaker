package uk.ac.manchester.cs.spinnaker.jobprocess.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    @SuppressWarnings("serial")
    private static final Set<String> IGNORED_EXTENSIONS =
            new HashSet<String>(){{
                add(".pyc");
            }};

    @SuppressWarnings("serial")
    private static final Set<String> IGNORED_DIRECTORIES =
            new HashSet<String>(){{
                add("application_generated_data_files");
                add("reports");
            }};

    private File workingDirectory = null;

    private Status status = null;

    private Throwable error = null;

    private List<File> outputs = new ArrayList<File>();

    private void gatherFiles(File directory, Collection<File> files) {
    	
        for (File file : directory.listFiles())
            if (file.isDirectory()) {
                if (!IGNORED_DIRECTORIES.contains(file.getName()))
                    gatherFiles(file, files);
            } else
                files.add(file);
    }

    private boolean isIgnored(File file) {
        int index = file.getName().lastIndexOf('.');
        if (index == -1)
            return false;
        String extension = file.getName().substring(index);
        return IGNORED_EXTENSIONS.contains(extension);
    }

    @Override
    public void execute(String machineUrl, SpinnakerMachine machine,
            PyNNJobParameters parameters, LogWriter logWriter) {
        try {
            status = Status.Running;
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
            Set<File> existingFiles = new HashSet<>();
            gatherFiles(workingDirectory, existingFiles);

            // Execute the program
			int exitValue = runSubprocess(parameters, logWriter);

            // Get any output files
            List<File> allFiles = new ArrayList<>();
            gatherFiles(workingDirectory, allFiles);
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
            status = Status.Finished;
        } catch (Throwable e) {
            error = e;
            status = Status.Error;
        }
    }

    private static final String SUBPROCESS_RUNNER = "python";
    private static final int FINALIZATION_DELAY = 1000;

    /** How to actually run a subprocess. */
    private int runSubprocess(PyNNJobParameters parameters, LogWriter logWriter)
			throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add(SUBPROCESS_RUNNER);

		Matcher scriptMatcher = Pattern.compile("([^\"]\\S*|\".+?\")\\s*")
				.matcher(parameters.getScript());
		while (scriptMatcher.find())
			command.add(scriptMatcher.group(1).replace("{system}", "spiNNaker"));

		ProcessBuilder builder = new ProcessBuilder(command);
		System.err.println("Running " + command + " in " + workingDirectory);
		builder.directory(workingDirectory);
		builder.redirectErrorStream(true);
		Process process = builder.start();

		// Run a thread to gather the log
		ReaderLogWriter logger = new ReaderLogWriter(process.getInputStream(),
				logWriter);
		logger.start();

		// Wait for the process to finish
		int exitValue = process.waitFor();
		Thread.sleep(FINALIZATION_DELAY);
		logger.close();
		return exitValue;
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
