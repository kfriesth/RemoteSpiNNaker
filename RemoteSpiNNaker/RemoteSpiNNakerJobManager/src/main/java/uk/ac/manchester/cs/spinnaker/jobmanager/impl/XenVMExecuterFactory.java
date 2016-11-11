package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import static com.xensource.xenapi.Types.VmPowerState.HALTED;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.ac.manchester.cs.spinnaker.job.JobManagerInterface.JOB_PROCESS_MANAGER_ZIP;
import static uk.ac.manchester.cs.spinnaker.jobmanager.JobManager.JOB_PROCESS_MANAGER_JAR;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.UUID;

import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;

import com.xensource.xenapi.Connection;
import com.xensource.xenapi.SR;
import com.xensource.xenapi.Session;
import com.xensource.xenapi.VBD;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VM;
import com.xensource.xenapi.Types.BadServerResponse;
import com.xensource.xenapi.Types.VbdMode;
import com.xensource.xenapi.Types.VbdType;
import com.xensource.xenapi.Types.VdiType;
import com.xensource.xenapi.Types.VmPowerState;
import com.xensource.xenapi.Types.XenAPIException;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuter;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;

public class XenVMExecuterFactory implements JobExecuterFactory {
	private final URL xenServerUrl;
	private final String username;
	private final String password;
	private final String templateLabel;
	private final boolean deleteOnExit;
	private final boolean shutdownOnExit;
	private final boolean liveUploadOutput;
	private final boolean requestSpiNNakerMachine;
	private final long defaultDiskSizeInGbs;
	private final int maxNVirtualMachines;
	private final Object lock = new Object();
	private final ThreadGroup threadGroup;

	private int nVirtualMachines = 0;
	private Logger logger = getLogger(getClass());

	public XenVMExecuterFactory(URL xenServerUrl, String username,
			String password, String templateLabel, long defaultDiskSizeInGbs,
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
		this.threadGroup = new ThreadGroup("XenVM");
	}

	@Override
	public JobExecuter createJobExecuter(JobManager manager, URL baseUrl)
			throws IOException {
		waitToClaimVM();

		try {
			return allocateVM(manager, baseUrl);
		} catch (Exception e) {
			throw new IOException("Error creating VM", e);
		}
	}

	private JobExecuter allocateVM(JobManager manager, URL baseUrl)
			throws MalformedURLException, XmlRpcException, IOException {
		String uuid = UUID.randomUUID().toString();

		URL jobProcessManagerUrl = new URL(baseUrl, "job/"
				+ JOB_PROCESS_MANAGER_ZIP);

		StringBuilder args = new StringBuilder("-jar ");
		args.append(JOB_PROCESS_MANAGER_JAR);
		args.append(" --serverUrl ");
		args.append(baseUrl.toString());
		args.append(" --executerId ");
		args.append(uuid);
		if (deleteOnExit)
			args.append(" --deleteOnExit");
		if (liveUploadOutput)
			args.append(" --liveUploadOutput");
		if (requestSpiNNakerMachine)
			args.append(" --requestMachine");

		return new Executer(manager, uuid, jobProcessManagerUrl,
				args.toString());
	}

	private void waitToClaimVM() {
		synchronized (lock) {
			logger.debug(nVirtualMachines + " of " + maxNVirtualMachines
					+ " in use");
			while (nVirtualMachines >= maxNVirtualMachines) {
				logger.debug("Waiting for a VM to become available ("
						+ nVirtualMachines + " of " + maxNVirtualMachines
						+ " in use)");
				try {
					lock.wait();
				} catch (InterruptedException e) {
					// Does Nothing
				}
			}
			nVirtualMachines++;
		}
	}

	protected void executorFinished() {
		synchronized (lock) {
			nVirtualMachines--;
			logger.debug(nVirtualMachines + " of " + maxNVirtualMachines
					+ " now in use");
			lock.notifyAll();
		}
	}

	private static final int MS_BETWEEN_CHECKS = 10000;

	class Executer implements JobExecuter, Runnable {
		// Parameters from constructor
		private final JobManager jobManager;
		private final String uuid;
		private final String jobProcessManagerUrl;
		private final String args;

		// Internal entities
		private Logger logger = getLogger(getClass());
		private Connection connection;
		private VM clonedVm;
		private VBD disk;
		private VDI vdi;
		private VDI extraVdi;
		private VBD extraDisk;

		public Executer(JobManager jobManager, String uuid,
				URL jobProcessManagerUrl, String args) throws XmlRpcException,
				IOException {
			this.jobManager = jobManager;
			this.uuid = uuid;
			this.jobProcessManagerUrl = jobProcessManagerUrl.toString();
			this.args = args;
		}

		@Override
		public String getExecuterId() {
			return uuid;
		}

		@Override
		public void startExecuter() {
			new Thread(threadGroup, this, "Executer:" + uuid).start();
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
					JOB_PROCESS_MANAGER_ZIP);
			clonedVm.addToXenstoreData(connection, "vm-data/nmpiargs", args);
			if (shutdownOnExit)
				clonedVm.addToXenstoreData(connection, "vm-data/shutdown", "true");
		}

		private void deleteVm() throws BadServerResponse, XenAPIException,
				XmlRpcException {
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

			executorFinished();
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
}
