package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import static com.xensource.xenapi.Types.VmPowerState.HALTED;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;

import com.xensource.xenapi.Connection;
import com.xensource.xenapi.SR;
import com.xensource.xenapi.Session;
import com.xensource.xenapi.Types.BadServerResponse;
import com.xensource.xenapi.Types.VbdMode;
import com.xensource.xenapi.Types.VbdType;
import com.xensource.xenapi.Types.VdiType;
import com.xensource.xenapi.Types.VmPowerState;
import com.xensource.xenapi.Types.XenAPIException;
import com.xensource.xenapi.VBD;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VM;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuter;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;

public class XenVMExecuter extends Thread implements JobExecuter {
    private static final int MS_BETWEEN_CHECKS = 10000;

    // Parameters from constructor
    private final JobManager jobManager;
    private final XenVMExecuterFactory factory;
    private final String uuid;
    private final URL xenServerUrl;
    private final String username;
    private final String password;
    private final String templateLabel;
    private final long defaultDiskSizeInGbs;
    private final String jobProcessManagerUrl;
    private final String jobProcessManagerZipFile;
    private final String args;
    private final boolean deleteOnExit;
    private final boolean shutdownOnExit;

    // Internal entities
    private Log logger = LogFactory.getLog(getClass());
    private Connection connection;
    private VM clonedVm;
    private VBD disk;
    private VDI vdi;
    private VDI extraVdi;
    private VBD extraDisk;

    public XenVMExecuter(
            JobManager jobManager, XenVMExecuterFactory factory, String uuid,
            URL xenServerUrl, String username, String password,
            String templateLabel, long defaultDiskSizeInGbs,
            String jobProcessManagerUrl, String jobProcessManagerZipFile,
            String args, boolean deleteOnExit, boolean shutdownOnExit)
                throws XmlRpcException, IOException {
        this.jobManager = jobManager;
        this.factory = factory;
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
        this.shutdownOnExit = shutdownOnExit;
    }

    @Override
    public String getExecuterId() {
        return uuid;
    }

    @Override
    public void startExecuter() {
        start();
    }

	private void createVm() throws XmlRpcException, IOException {
		connection = new Connection(xenServerUrl);
		Session.loginWithPassword(connection, username, password);

		Set<VM> vmsWithLabel = VM.getByNameLabel(connection, templateLabel);
		if (vmsWithLabel.isEmpty())
			throw new IOException("No template with name " + templateLabel
					+ " was found");

		VM templateVm = vmsWithLabel.iterator().next();
		clonedVm = templateVm.createClone(connection, templateLabel + "_"
				+ uuid);
		Set<VBD> disks = clonedVm.getVBDs(connection);
		if (disks.isEmpty())
			throw new IOException("No disks found on " + templateLabel);

		disk = disks.iterator().next();
		vdi = disk.getVDI(connection);
		String diskLabel = vdi.getNameLabel(connection);
		vdi.setNameLabel(connection, diskLabel + "_" + uuid + "_base");
		SR storageRepository = vdi.getSR(connection);

		VDI.Record vdiRecord = new VDI.Record();
		vdiRecord.nameLabel = diskLabel + "_" + uuid + "_storage";
		vdiRecord.type = VdiType.USER;
		vdiRecord.SR = storageRepository;
		vdiRecord.virtualSize = defaultDiskSizeInGbs * 1024L * 1024L * 1024L;
		extraVdi = VDI.create(connection, vdiRecord);
		VBD.Record vbdRecord = new VBD.Record();
		vbdRecord.VM = clonedVm;
		vbdRecord.VDI = extraVdi;
		vbdRecord.userdevice = "1";
		vbdRecord.mode = VbdMode.RW;
		vbdRecord.type = VbdType.DISK;
		extraDisk = VBD.create(connection, vbdRecord);

		clonedVm.addToXenstoreData(connection, "vm-data/nmpiurl",
				jobProcessManagerUrl);
		clonedVm.addToXenstoreData(connection, "vm-data/nmpifile",
				jobProcessManagerZipFile);
		clonedVm.addToXenstoreData(connection, "vm-data/nmpiargs", args);
		if (shutdownOnExit)
			clonedVm.addToXenstoreData(connection, "vm-data/shutdown", "true");
    }

    private void deleteVm()
            throws BadServerResponse, XenAPIException, XmlRpcException {
        if (connection != null) {
            if (disk != null)
                disk.destroy(connection);
            if (extraDisk != null)
                extraDisk.destroy(connection);
            if (vdi != null)
                vdi.destroy(connection);
            if (extraVdi != null)
                extraVdi.destroy(connection);
            if (clonedVm != null)
                clonedVm.destroy(connection);
        }
    }

    @Override
	public void run() {
        try {
            synchronized (this) {
                createVm();
                clonedVm.start(connection, false, true);
            }
            waitWhileVMRunning();
            jobManager.setExecutorExited(uuid, null);
        } catch (Throwable e) {
            logger.error("Error setting up VM", e);
            jobManager.setExecutorExited(uuid, e.getMessage());
        }

		try {
			if (deleteOnExit)
				synchronized (this) {
					deleteVm();
				}
		} catch (Throwable e) {
			logger.error("Error deleting VM");
		}

        factory.executorFinished();
    }

	private void waitWhileVMRunning() {
		while (true) {
			try {
				Thread.sleep(MS_BETWEEN_CHECKS);
			} catch (InterruptedException e) {
				// Does Nothing
			}
			try {
				VmPowerState powerState = clonedVm.getPowerState(connection);
				logger.debug("VM for " + uuid + " is in state " + powerState);
				if (powerState == HALTED)
					break;
			} catch (Exception e) {
				logger.error("Could not get VM power state, assuming off", e);
				break;
			}
		}
	}
}
