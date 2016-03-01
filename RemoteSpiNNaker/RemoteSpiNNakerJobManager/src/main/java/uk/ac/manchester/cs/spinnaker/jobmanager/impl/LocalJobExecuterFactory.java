package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuter;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;

public class LocalJobExecuterFactory implements JobExecuterFactory {

    private static final String JOB_PROCESS_MANAGER_MAIN_CLASS =
            "uk.ac.manchester.cs.spinnaker.jobprocessmanager.JobProcessManager";

    private static File getJavaExec() throws IOException {
        File binDir = new File(System.getProperty("java.home"), "bin");
        File exec = new File(binDir, "java");
        if (!exec.canExecute()) {
            exec = new File(binDir, "java.exe");
        }
        return exec;
    }

    private List<File> jobProcessManagerClasspath = new ArrayList<File>();

    private File jobExecuterDirectory = null;

    public LocalJobExecuterFactory() throws IOException {

        // Create a temporary folder
        jobExecuterDirectory = File.createTempFile("jobExecuter", "tmp");
        jobExecuterDirectory.delete();
        jobExecuterDirectory.mkdirs();
        jobExecuterDirectory.deleteOnExit();

        // Find the JobManager resource
        InputStream jobManagerStream = getClass().getResourceAsStream(
            JobManager.JOB_PROCESS_MANAGER_ZIP);
        if (jobManagerStream == null) {
            throw new UnsatisfiedLinkError(JobManager.JOB_PROCESS_MANAGER_ZIP
                    + " not found in classpath");
        }

        // Extract the JobManager resources
        ZipInputStream input = new ZipInputStream(jobManagerStream);
        ZipEntry entry = input.getNextEntry();
        while (entry != null) {
            File entryFile = new File(jobExecuterDirectory,
                    entry.getName());
            if (!entry.isDirectory()) {
                entryFile.getParentFile().mkdirs();
                FileOutputStream output = new FileOutputStream(entryFile);
                byte[] buffer = new byte[8196];
                int bytesRead = input.read(buffer);
                while (bytesRead != -1) {
                    output.write(buffer, 0, bytesRead);
                    bytesRead = input.read(buffer);
                }
                output.close();
                entryFile.deleteOnExit();

                if (entryFile.getName().endsWith(".jar")) {
                    jobProcessManagerClasspath.add(entryFile);
                }
            }
            entry = input.getNextEntry();
        }
        input.close();
    }

    @Override
    public JobExecuter createJobExecuter(
            JobManager manager, URL baseUrl, String id) throws IOException {
        List<String> arguments = new ArrayList<String>();
        arguments.add("--serverUrl");
        arguments.add(baseUrl.toString());
        arguments.add("--local");
        arguments.add("--deleteOnExit");
        arguments.add("--executerId");
        arguments.add(id);
        return new LocalJobExecuter(manager, getJavaExec(),
            jobProcessManagerClasspath, JOB_PROCESS_MANAGER_MAIN_CLASS,
            arguments, jobExecuterDirectory, id);
    }

}
