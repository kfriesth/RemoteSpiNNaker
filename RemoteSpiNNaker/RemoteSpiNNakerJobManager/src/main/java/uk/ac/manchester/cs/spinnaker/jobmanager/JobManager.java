package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.JobSpecification;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTrace;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTraceElement;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.MachineManager;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueListener;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueManager;

/**
 * The manager of jobs; synchronizes and manages all the ongoing and future
 * processes and machines.
 */
public class JobManager implements NMPIQueueListener, JobManagerInterface {

    private static final String JOB_PROCESS_MANAGER_ZIP =
            "/RemoteSpiNNakerJobProcessManager.zip";

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

    private MachineManager machineManager = null;

    private NMPIQueueManager queueManager = null;

    private List<JobParametersFactory> jobParametersFactories = null;

    private List<File> jobProcessManagerClasspath = new ArrayList<File>();

    private URL baseUrl = null;

    private Log logger = LogFactory.getLog(getClass());

    private Map<Integer, SpinnakerMachine> allocatedMachines =
            new HashMap<Integer, SpinnakerMachine>();

    public JobManager(MachineManager machineManager,
            NMPIQueueManager queueManager,
            List<JobParametersFactory> jobParametersFactories,
            URL baseUrl)
                    throws IOException {
        this.machineManager = machineManager;
        this.queueManager = queueManager;
        this.jobParametersFactories = jobParametersFactories;
        this.baseUrl = baseUrl;

        // Create a temporary folder for the job process manager
        File jobProcessManagerDirectory =
                File.createTempFile("processManager", "tmp");
        jobProcessManagerDirectory.delete();
        jobProcessManagerDirectory.mkdirs();
        jobProcessManagerDirectory.deleteOnExit();

        // Find the JobManager resource
        InputStream jobManagerStream = getClass().getResourceAsStream(
                JOB_PROCESS_MANAGER_ZIP);
        if (jobManagerStream == null) {
            throw new UnsatisfiedLinkError(JOB_PROCESS_MANAGER_ZIP
                    + " not found in classpath");
        }

        // Extract the JobManager resources
        ZipInputStream input = new ZipInputStream(jobManagerStream);
        ZipEntry entry = input.getNextEntry();
        while (entry != null) {
            File entryFile = new File(jobProcessManagerDirectory,
                    entry.getName());
            if (!entry.isDirectory()) {
                logger.debug("Extracting to " + entryFile);
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
            } else {
                logger.debug(entryFile + " is directory");
            }
            entry = input.getNextEntry();
        }
        input.close();

        // Start the queue manager
        queueManager.addListener(this);
        queueManager.start();
    }

    @Override
    public void addJob(int id, String experimentDescription,
            List<String> inputData, Map<String, Object> hardwareConfig,
            boolean deleteJobOnExit) throws IOException {
        logger.info("New job " + id);

        // Get a machine to run the job on
        SpinnakerMachine machine = machineManager.getNextAvailableMachine();
        synchronized (allocatedMachines) {
            allocatedMachines.put(id, machine);
        }
        logger.info("Running " + id + " on " + machine.getMachineName());

        // Get job parameters if possible
        File workingDirectory = File.createTempFile("job", ".tmp");
        workingDirectory.delete();
        workingDirectory.mkdirs();
        JobParameters parameters = null;
        Map<String, JobParametersFactoryException> errors =
                new HashMap<String, JobParametersFactoryException>();
        for (JobParametersFactory factory : jobParametersFactories) {
            try {
                parameters = factory.getJobParameters(experimentDescription,
                        inputData, hardwareConfig, workingDirectory,
                        deleteJobOnExit);
                break;
            } catch (UnsupportedJobException e) {

                // Do Nothing
            } catch (JobParametersFactoryException e) {
                errors.put(factory.getClass().getSimpleName(), e);
            }
        }
        if (parameters == null) {
            synchronized (allocatedMachines) {
                allocatedMachines.remove(id);
                machineManager.releaseMachine(machine);
            }
            if (!errors.isEmpty()) {
                StringBuilder problemBuilder = new StringBuilder();
                problemBuilder.append("The job type was recognised by at least"
                        + " one factory, but could not be decoded.  The errors "
                        + " are as follows:\n");
                for (String key : errors.keySet()) {
                    JobParametersFactoryException error = errors.get(key);
                    problemBuilder.append(key);
                    problemBuilder.append(": ");
                    problemBuilder.append(error.getMessage());
                    problemBuilder.append('\n');
                }
                throw new IOException(problemBuilder.toString());
            }
            throw new IOException(
                    "The job did not appear to be supported on this system");
        }

        // Get any requested input files
        if (inputData != null) {
            for (String input : inputData) {
                URL inputUrl = new URL(input);
                FileDownloader.downloadFile(inputUrl, workingDirectory, null);
            }
        }

        JobExecuter executer = new JobExecuter(getJavaExec(),
                jobProcessManagerClasspath,
                JOB_PROCESS_MANAGER_MAIN_CLASS,
                new ArrayList<String>(), workingDirectory);
        executer.start();
        OutputStream output = new UnclosableOutputStream(
                executer.getProcessOutputStream());

        // Send the job executed the details of what to run
        ObjectMapper mapper = new ObjectMapper();
        JobParametersSerializer serializer =
                new JobParametersSerializer("jobType");
        SimpleModule serializerModule = new SimpleModule();
        serializerModule.addSerializer(JobParameters.class, serializer);
        mapper.registerModule(serializerModule);

        logger.debug("Writing specification");
        mapper.writeValue(output, new JobSpecification(machine, parameters, id,
                baseUrl.toString()));
        output.flush();
        logger.debug("Finished writing");
    }

    @Override
    public void appendLog(int id, String logToAppend) {
        logger.debug("Updating log for " + id);
        queueManager.appendJobLog(id, logToAppend);
    }

    @Override
    public void setJobFinished(int id, String logToAppend,
            List<String> outputs) {
        logger.debug("Marking job " + id + " as finished");
        synchronized (allocatedMachines) {
            SpinnakerMachine machine = allocatedMachines.remove(id);
            machineManager.releaseMachine(machine);
        }
        List<File> outputFiles = new ArrayList<File>();
        for (String filename : outputs) {
            outputFiles.add(new File(filename));
        }
        try {
            queueManager.setJobFinished(id, logToAppend, outputFiles);
        } catch (MalformedURLException e) {
            logger.error("Error creating URLs while updating job", e);
        }
    }

    @Override
    public void setJobError(int id, String error, String logToAppend,
            List<String> outputs, RemoteStackTrace stackTrace) {
        logger.debug("Marking job " + id + " as error");
        synchronized (allocatedMachines) {
            SpinnakerMachine machine = allocatedMachines.remove(id);
            machineManager.releaseMachine(machine);
        }
        StackTraceElement[] elements =
                new StackTraceElement[stackTrace.getElements().size()];
        int i = 0;
        for (RemoteStackTraceElement element : stackTrace.getElements()) {
            elements[i++] = new StackTraceElement(element.getClassName(),
                    element.getMethodName(), element.getFileName(),
                    element.getLineNumber());
        }
        List<File> outputFiles = new ArrayList<File>();
        for (String filename : outputs) {
            outputFiles.add(new File(filename));
        }

        Exception exception = new Exception(error);
        exception.setStackTrace(elements);
        try {
            queueManager.setJobError(id, logToAppend, outputFiles, exception);
        } catch (MalformedURLException e) {
            logger.error("Error creating URLs while updating job", e);
        }
    }
}
