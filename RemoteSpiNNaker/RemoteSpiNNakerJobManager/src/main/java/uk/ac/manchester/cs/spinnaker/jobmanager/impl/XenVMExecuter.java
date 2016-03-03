package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;

import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Session;
import com.xensource.xenapi.Types.VmPowerState;
import com.xensource.xenapi.VM;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuter;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;

public class XenVMExecuter extends Thread implements JobExecuter {

    private static final int MS_BETWEEN_CHECKS = 10000;

    private JobManager jobManager;

    private Connection connection;

    private VM clonedVm;

    private String uuid;

    private Log logger = LogFactory.getLog(getClass());

    public XenVMExecuter(
            JobManager jobManager, URL xenServerUrl, String username,
            String password, String templateLabel, URL baseServerUrl)
                throws XmlRpcException, IOException {
        this.jobManager = jobManager;
        this.connection = new Connection(xenServerUrl);
        Session.loginWithPassword(connection, username, password);

        String uuid = UUID.randomUUID().toString();
        Set<VM> vmsWithLabel = VM.getByNameLabel(
            connection, templateLabel);
        if (vmsWithLabel.isEmpty()) {
            throw new IOException(
                "No template with name " + templateLabel + " was found");
        }
        VM templateVm = vmsWithLabel.iterator().next();
        this.clonedVm = templateVm.createClone(
            connection, templateLabel + uuid);
        this.uuid = clonedVm.getUuid(connection);
    }

    @Override
    public String getExecuterId() {
        return uuid;
    }

    @Override
    public void startExecuter() {
        start();
    }

    public void run() {
        try {
            clonedVm.start(connection, false, true);
        } catch (Exception e) {
            jobManager.setExecutorExited(uuid, e.getMessage());
        }

        boolean vmRunning = true;
        while (vmRunning) {
            try {
                Thread.sleep(MS_BETWEEN_CHECKS);
            } catch (InterruptedException e) {

                // Does Nothing
            }
            try {
                VmPowerState powerState = clonedVm.getPowerState(connection);
                logger.debug("VM for " + uuid + " is in state " + powerState);
                if (powerState == VmPowerState.HALTED) {
                    vmRunning = false;
                }
            } catch (Exception e) {
                logger.error("Could not get VM power state, assuming off", e);
                vmRunning = false;
            }
        }
        jobManager.setExecutorExited(uuid, null);
    }
}
