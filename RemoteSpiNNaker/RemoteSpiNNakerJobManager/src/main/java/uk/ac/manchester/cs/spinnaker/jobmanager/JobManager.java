package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTrace;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTraceElement;
import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.MachineManager;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueListener;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueManager;
import uk.ac.manchester.cs.spinnaker.output.OutputManager;

/**
 * The manager of jobs; synchronises and manages all the ongoing and future
 * processes and machines.
 */
public class JobManager implements NMPIQueueListener, JobManagerInterface {

    public static final String JOB_PROCESS_MANAGER_JAR =
            "RemoteSpiNNakerJobProcessManager.jar";

    private MachineManager machineManager = null;

    private NMPIQueueManager queueManager = null;

    private URL baseUrl = null;

    private Log logger = LogFactory.getLog(getClass());

    private Map<Integer, SpinnakerMachine> allocatedMachines =
        new HashMap<Integer, SpinnakerMachine>();

    private JobExecuterFactory jobExecuterFactory = null;

    private Queue<Job> jobsToRun = new LinkedList<Job>();

    private Map<String, JobExecuter> jobExecuters =
        new HashMap<String, JobExecuter>();

    private Map<String, Job> executorJobId = new HashMap<String, Job>();

    private Map<Integer, File> jobOutputTempFiles =
        new HashMap<Integer, File>();

    private OutputManager outputManager = null;

    public JobManager(MachineManager machineManager,
            NMPIQueueManager queueManager, OutputManager outputManager,
            URL baseUrl, JobExecuterFactory jobExecuterFactory) {
        this.machineManager = machineManager;
        this.queueManager = queueManager;
        this.outputManager = outputManager;
        this.baseUrl = baseUrl;
        this.jobExecuterFactory = jobExecuterFactory;

        // Start the queue manager
        queueManager.addListener(this);
        queueManager.start();
    }

    @Override
    public void addJob(Job job) throws IOException {
        logger.info("New job " + job.getId());

        // Add the job to the set of jobs to be run
        synchronized (jobExecuters) {
            synchronized (jobsToRun) {
                jobsToRun.add(job);
                jobsToRun.notifyAll();
            }

            // Start an executer for the job
            JobExecuter executer = jobExecuterFactory.createJobExecuter(
                this, baseUrl);
            jobExecuters.put(executer.getExecuterId(), executer);
            executer.startExecuter();
        }
    }

    @Override
    public Job getNextJob(String executerId) {
        synchronized (jobsToRun) {
            while (jobsToRun.isEmpty()) {
                try {
                    jobsToRun.wait();
                } catch (InterruptedException e) {

                    // Does Nothing
                }
            }
            Job job = jobsToRun.poll();
            executorJobId.put(executerId, job);
            logger.info(
                "Executer " + executerId + " is running " + job.getId());
            queueManager.setJobRunning(job.getId());
            return job;
        }
    }

    @Override
    public SpinnakerMachine getJobMachine(
            int id, int nChips, int nMachineTimeSteps, double timescaleFactor) {

        int nChipsToRequest = nChips;
        if (nChips <= 0) {
            nChipsToRequest = 48 * 3;
        }

        // Get a machine to run the job on
        SpinnakerMachine machine = machineManager.getNextAvailableMachine(
            nChipsToRequest);
        synchronized (allocatedMachines) {
            allocatedMachines.put(id, machine);
        }
        logger.info("Running " + id + " on " + machine.getMachineName());
        return machine;
    }

    @Override
    public void appendLog(int id, String logToAppend) {
        logger.debug("Updating log for " + id);
        logger.trace(id + ": " + logToAppend);
        queueManager.appendJobLog(id, logToAppend);
    }

    @Override
    public void addOutput(
            String projectId, int id, String output, InputStream input) {
        if (!jobOutputTempFiles.containsKey(id)) {
            try {
                File tempOutputDir = File.createTempFile("jobOutput", ".tmp");
                tempOutputDir.delete();
                tempOutputDir.mkdirs();
                jobOutputTempFiles.put(id, tempOutputDir);
            } catch (IOException e) {
                logger.error(
                    "Error creating temporary output directory for " + id, e);
                throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
            }
        }

        File outputFile = new File(jobOutputTempFiles.get(id), output);
        try {
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            byte[] buffer = new byte[8096];
            int bytesRead = input.read(buffer);
            while (bytesRead != -1) {
                outputStream.write(buffer, 0, bytesRead);
                bytesRead = input.read(buffer);
            }
            outputStream.close();
        } catch (IOException e) {
            logger.error("Error writing file " + outputFile + " for job " + id,
                e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void getFilesRecursively(File directory, List<File> files) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                getFilesRecursively(file, files);
            } else {
                files.add(file);
            }
        }
    }

    private List<DataItem> getOutputFiles(
            String projectId, int id, String baseFile, List<String> outputs)
            throws IOException {
        List<DataItem> outputItems = new ArrayList<DataItem>();
        if (outputs != null) {
            List<File> outputFiles = new ArrayList<File>();
            for (String filename : outputs) {
                outputFiles.add(new File(filename));
            }
            outputItems.addAll(outputManager.addOutputs(
                projectId, id, new File(baseFile), outputFiles));
        }
        if (jobOutputTempFiles.containsKey(id)) {
            List<File> outputFiles = new ArrayList<File>();
            File directory = jobOutputTempFiles.get(id);
            getFilesRecursively(directory, outputFiles);
            outputItems.addAll(outputManager.addOutputs(
                projectId, id, directory, outputFiles));
        }
        return outputItems;
    }

    @Override
    public void setJobFinished(
            String projectId, int id, String logToAppend,
            String baseDirectory, List<String> outputs) {
        logger.debug("Marking job " + id + " as finished");
        synchronized (allocatedMachines) {
            SpinnakerMachine machine = allocatedMachines.remove(id);
            if (machine != null) {
                machineManager.releaseMachine(machine);
            }
        }

        try {
            queueManager.setJobFinished(id, logToAppend,
                getOutputFiles(projectId, id, baseDirectory, outputs));
        } catch (IOException e) {
            logger.error("Error creating URLs while updating job", e);
        }
    }

    @Override
    public void setJobError(String projectId, int id, String error,
            String logToAppend, String baseDirectory, List<String> outputs,
            RemoteStackTrace stackTrace) {
        logger.debug("Marking job " + id + " as error");
        synchronized (allocatedMachines) {
            SpinnakerMachine machine = allocatedMachines.remove(id);
            if (machine != null) {
                machineManager.releaseMachine(machine);
            }
        }
        StackTraceElement[] elements =
                new StackTraceElement[stackTrace.getElements().size()];
        int i = 0;
        for (RemoteStackTraceElement element : stackTrace.getElements()) {
            elements[i++] = new StackTraceElement(element.getClassName(),
                    element.getMethodName(), element.getFileName(),
                    element.getLineNumber());
        }

        Exception exception = new Exception(error);
        exception.setStackTrace(elements);
        try {
            queueManager.setJobError(id, logToAppend,
                getOutputFiles(
                    projectId, id, baseDirectory, outputs), exception);
        } catch (IOException e) {
            logger.error("Error creating URLs while updating job", e);
        }
    }

    public void setExecutorExited(String executorId, String logToAppend) {
        Job job = executorJobId.remove(executorId);
        jobExecuters.remove(executorId);
        if (job != null) {
            logger.debug("Job " + job.getId() + " has exited");

            boolean alreadyGone = false;
            synchronized (allocatedMachines) {
                SpinnakerMachine machine = allocatedMachines.remove(
                    job.getId());
                if (machine != null) {
                    machineManager.releaseMachine(machine);
                } else {
                    alreadyGone = true;
                }
            }

            if (!alreadyGone) {
                logger.debug("Job " + job.getId() + " has not exited cleanly");
                try {
                    String projectId = new File(job.getCollabId()).getName();
                    queueManager.setJobError(job.getId(), logToAppend,
                        getOutputFiles(projectId, job.getId(), null, null),
                        new Exception("Job did not finish cleanly"));
                } catch (IOException e) {
                    logger.error("Error creating URLs while updating job", e);
                }
            }
        } else {
            logger.error(
                "An executer has exited.  This could indicate an error!");
            logger.error(logToAppend);

            synchronized (jobsToRun) {
                synchronized (jobExecuters) {
                    if (jobsToRun.size() > jobExecuters.size()) {
                        try {
                            JobExecuter executer =
                                jobExecuterFactory.createJobExecuter(
                                    this, baseUrl);
                            jobExecuters.put(
                                executer.getExecuterId(), executer);
                            executer.startExecuter();
                        } catch (IOException e) {
                            logger.error("Could not launch a new executer", e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public Response getJobProcessManager() {
        InputStream jobManagerStream = getClass().getResourceAsStream(
            "/" + JobManager.JOB_PROCESS_MANAGER_ZIP);
        if (jobManagerStream == null) {
            throw new UnsatisfiedLinkError(JobManager.JOB_PROCESS_MANAGER_ZIP
                    + " not found in classpath");
        }
        return Response.ok(jobManagerStream).type("application/zip").build();
    }
}
