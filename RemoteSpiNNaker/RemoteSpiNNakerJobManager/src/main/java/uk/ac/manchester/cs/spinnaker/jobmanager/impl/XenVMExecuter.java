package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;

import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Session;
import com.xensource.xenapi.Types.VmPowerState;
import com.xensource.xenapi.VBD;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VM;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuter;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;

public class XenVMExecuter extends Thread implements JobExecuter {

    private static final int MS_BETWEEN_CHECKS = 10000;

    private JobManager jobManager;

    private URL xenServerUrl;

    private String username;

    private String password;

    private String templateLabel;

    private long defaultDiskSizeInGbs;

    private String jobProcessManagerUrl;

    private String jobProcessManagerZipFile;

    private boolean deleteOnExit;

    private String args;

    private String uuid;

    private Log logger = LogFactory.getLog(getClass());

    public XenVMExecuter(
            JobManager jobManager, String uuid, URL xenServerUrl,
            String username, String password, String templateLabel,
            long defaultDiskSizeInGbs, String jobProcessManagerUrl,
            String jobProcessManagerZipFile, String args, boolean deleteOnExit)
                throws XmlRpcException, IOException {
        this.jobManager = jobManager;
        this.uuid = uuid;
        this.xenServerUrl = xenServerUrl;
        this.username = username;
        this.password = password;
        this.templateLabel = templateLabel;
        this.defaultDiskSizeInGbs = defaultDiskSizeInGbs;
        this.jobProcessManagerUrl = jobProcessManagerUrl;
        this.jobProcessManagerZipFile = jobProcessManagerZipFile;
        this.args = args;
        this.deleteOnExit = deleteOnExit;
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

            Connection connection = new Connection(xenServerUrl);
            Session.loginWithPassword(connection, username, password);

            Set<VM> vmsWithLabel = VM.getByNameLabel(
                connection, templateLabel);
            if (vmsWithLabel.isEmpty()) {
                throw new IOException(
                    "No template with name " + templateLabel + " was found");
            }
            VM templateVm = vmsWithLabel.iterator().next();
            VM clonedVm = templateVm.createClone(
                connection, templateLabel + "_" + uuid);
            Set<VBD> disks = clonedVm.getVBDs(connection);
            if (disks.isEmpty()) {
                throw new IOException("No disks found on " + templateLabel);
            }
            VBD disk = disks.iterator().next();
            VDI vdi = disk.getVDI(connection);
            vdi.setNameLabel(
                connection, vdi.getNameLabel(connection) + "_" + uuid);
            long currentSize = vdi.getVirtualSize(connection);
            long newSize =
                currentSize + (defaultDiskSizeInGbs * 1024L * 1024L * 1024L);
            vdi.resize(connection, newSize);

            clonedVm.addToXenstoreData(
                connection, "vm-data/nmpiurl", jobProcessManagerUrl);
            clonedVm.addToXenstoreData(
                connection, "vm-data/nmpifile", jobProcessManagerZipFile);
            clonedVm.addToXenstoreData(connection, "vm-data/nmpiargs", args);
            clonedVm.start(connection, false, true);

            boolean vmRunning = true;
            while (vmRunning) {
                try {
                    Thread.sleep(MS_BETWEEN_CHECKS);
                } catch (InterruptedException e) {

                    // Does Nothing
                }
                try {
                    VmPowerState powerState = clonedVm.getPowerState(
                        connection);
                    logger.debug(
                        "VM for " + uuid + " is in state " + powerState);
                    if (powerState == VmPowerState.HALTED) {
                        vmRunning = false;
                    }
                } catch (Exception e) {
                    logger.error(
                        "Could not get VM power state, assuming off", e);
                    vmRunning = false;
                }
            }

            if (deleteOnExit) {
                clonedVm.destroy(connection);
                vdi.destroy(connection);
            }
        } catch (Exception e) {
            logger.error("Error setting up VM", e);
            jobManager.setExecutorExited(uuid, e.getMessage());
        }

        jobManager.setExecutorExited(uuid, null);
    }
}
