package uk.ac.manchester.cs.spinnaker.jobprocess.impl;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private File workingDirectory = null;

    private boolean deleteOnCleanup = false;

    private Status status = null;

    private Throwable error = null;

    private List<File> outputs = new ArrayList<File>();

    private void gatherFiles(File directory, Collection<File> files) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                gatherFiles(file, files);
            } else {
                files.add(file);
            }
        }
    }

    private boolean isIgnored(File file) {
        int index = file.getName().lastIndexOf('.');
        if (index == -1) {
            return false;
        }
        String extension = file.getName().substring(index);
        return IGNORED_EXTENSIONS.contains(extension);
    }

    @Override
    public void execute(SpinnakerMachine machine,
            PyNNJobParameters parameters, LogWriter logWriter) {
        try {
            status = Status.Running;
            workingDirectory = new File(parameters.getWorkingDirectory());
            deleteOnCleanup = parameters.isDeleteOnCompletion();

            // Deal with hardware configuration
            File cfgFile = new File(workingDirectory, "spynnaker.cfg");
            String hardwareConfiguration =
                    parameters.getHardwareConfiguration().trim();
            if (!hardwareConfiguration.isEmpty()) {
                PrintWriter writer = new PrintWriter(cfgFile);
                writer.println(hardwareConfiguration);
                writer.close();
            }

            // Add the details of the machine
            ConfigParser parser = new ConfigParser();
            if (cfgFile.exists()) {
                parser.read(cfgFile);
            }
            parser.set("Machine", "machineName", machine.getMachineName());
            parser.set("Machine", "version", machine.getVersion());
            parser.set("Machine", "width", machine.getWidth());
            parser.set("Machine", "height", machine.getHeight());
            parser.set("Machine", "bmp_names", machine.getBmpDetails());

            // Keep existing files to compare to later
            Set<File> existingFiles = new HashSet<File>();
            gatherFiles(workingDirectory, existingFiles);

            // Execute the program
            List<String> command = new ArrayList<String>();
            command.add("python");
            command.add(parameters.getScript());
            command.add("spiNNaker");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            // Run a thread to gather the log
            ReaderLogWriter logger = new ReaderLogWriter(
                    process.getInputStream(), logWriter);
            logger.start();

            // Wait for the process to finish
            int exitValue = process.waitFor();
            Thread.sleep(1000);
            logger.close();

            // Get any output files
            List<File> allFiles = new ArrayList<File>();
            gatherFiles(workingDirectory, allFiles);
            for (File file : allFiles) {

                if (!existingFiles.contains(file) && !isIgnored(file)) {
                    outputs.add(file);
                }
            }

            // If the exit is an error, mark an error
            if (exitValue != 0) {
                throw new Exception("Python exited with a non-zero code ("
                        + exitValue + ")");
            }
            status = Status.Finished;
        } catch (Throwable e) {
            error = e;
            status = Status.Error;
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

    private void deleteDirectory(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                deleteDirectory(file);
            } else {
                file.delete();
            }
        }
        directory.delete();
    }

    @Override
    public void cleanup() {
        if (deleteOnCleanup) {
            deleteDirectory(workingDirectory);
        }
    }
}
