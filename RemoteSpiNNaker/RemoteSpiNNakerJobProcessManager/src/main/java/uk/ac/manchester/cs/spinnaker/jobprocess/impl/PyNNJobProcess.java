package uk.ac.manchester.cs.spinnaker.jobprocess.impl;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Override
    public void execute(SpinnakerMachine machine,
            PyNNJobParameters parameters, LogWriter logWriter) {
        try {
            workingDirectory = new File(parameters.getWorkingDirectory());
            deleteOnCleanup = parameters.isDeleteOnCompletion();

            File pacmanCfg = new File(workingDirectory, "pacman.cfg");
            PrintWriter writer = new PrintWriter(pacmanCfg);
            writer.println("[Machine]");
            writer.println("machine = " + machine.getMachineName());
            writer.println("version = " + machine.getVersion());
            writer.close();

            // Keep existing files to compare to later
            Set<File> existingFiles = new HashSet<File>();
            gatherFiles(workingDirectory, existingFiles);

            // Execute the program
            List<String> command = new ArrayList<String>();
            command.add("python");
            command.add(parameters.getScript());
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
            logger.close();

            // Get any output files
            List<File> allFiles = new ArrayList<File>();
            gatherFiles(workingDirectory, allFiles);
            for (File file : allFiles) {
            	if (!existingFiles.contains(file)) {
            		outputs.add(file);
            	}
            }

            // If the exit is an error, mark an error
            if (exitValue != 0) {
            	throw new Exception("Python exited with a non-zero code ("
                        + exitValue + ")");
            }
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
