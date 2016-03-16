package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuter;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;

public class XenVMExecuterFactory implements JobExecuterFactory {

    private URL xenServerUrl = null;

    private String username = null;

    private String password = null;

    private String templateLabel = null;

    private boolean deleteOnExit;

    private boolean shutdownOnExit;

    private boolean liveUploadOutput;

    private boolean requestSpiNNakerMachine;

    private long defaultDiskSizeInGbs;

    private Integer maxNVirtualMachines;

    private int nVirtualMachines = 0;

    public XenVMExecuterFactory(
            URL xenServerUrl, String username, String password,
            String templateLabel, long defaultDiskSizeInGbs,
            boolean deleteOnExit, boolean shutdownOnExit,
            boolean liveUploadOutput, boolean requestSpiNNakerMachine,
            int maxNVirtualMachines) {
        this.xenServerUrl = xenServerUrl;
        this.username = username;
        this.password = password;
        this.templateLabel = templateLabel;
        this.deleteOnExit = deleteOnExit;
        this.shutdownOnExit = shutdownOnExit;
        this.liveUploadOutput = liveUploadOutput;
        this.requestSpiNNakerMachine = requestSpiNNakerMachine;
        this.defaultDiskSizeInGbs = defaultDiskSizeInGbs;
        this.maxNVirtualMachines = maxNVirtualMachines;
    }

    @Override
    public JobExecuter createJobExecuter(JobManager manager, URL baseUrl)
            throws IOException {

        synchronized (maxNVirtualMachines) {
            while (nVirtualMachines >= maxNVirtualMachines) {
                try {
                    maxNVirtualMachines.wait();
                } catch (InterruptedException e) {

                    // Does Nothing
                }
            }
            nVirtualMachines += 1;
        }

        try {
            String uuid = UUID.randomUUID().toString();

            URL jobProcessManagerUrl = new URL(
                baseUrl, "job/" + JobManager.JOB_PROCESS_MANAGER_ZIP);

            StringBuilder args = new StringBuilder("-jar ");
            args.append(JobManager.JOB_PROCESS_MANAGER_JAR);
            args.append(" --serverUrl ");
            args.append(baseUrl.toString());
            args.append(" --executerId ");
            args.append(uuid);
            if (deleteOnExit) {
                args.append(" --deleteOnExit");
            }
            if (liveUploadOutput) {
                args.append(" --liveUploadOutput");
            }
            if (requestSpiNNakerMachine) {
                args.append(" --requestMachine");
            }

            return new XenVMExecuter(
                manager, this, uuid, xenServerUrl, username, password,
                templateLabel, defaultDiskSizeInGbs,
                jobProcessManagerUrl.toString(),
                JobManager.JOB_PROCESS_MANAGER_ZIP, args.toString(),
                deleteOnExit, shutdownOnExit);
        } catch (Exception e) {
            throw new IOException("Error creating VM", e);
        }
    }

    protected void executorFinished() {
        synchronized (maxNVirtualMachines) {
            nVirtualMachines -= 1;
            maxNVirtualMachines.notifyAll();
        }
    }

}
