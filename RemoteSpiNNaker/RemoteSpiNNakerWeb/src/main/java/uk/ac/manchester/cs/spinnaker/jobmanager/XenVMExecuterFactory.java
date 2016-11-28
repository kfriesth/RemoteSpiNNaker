package uk.ac.manchester.cs.spinnaker.jobmanager;

import static com.xensource.xenapi.Session.loginWithPassword;
import static com.xensource.xenapi.Session.logout;
import static com.xensource.xenapi.Types.VbdMode.RW;
import static com.xensource.xenapi.Types.VbdType.DISK;
import static com.xensource.xenapi.Types.VdiType.USER;
import static com.xensource.xenapi.Types.VmPowerState.HALTED;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.ac.manchester.cs.spinnaker.job.JobManagerInterface.JOB_PROCESS_MANAGER_ZIP;
import static uk.ac.manchester.cs.spinnaker.jobmanager.JobManager.JOB_PROCESS_MANAGER_JAR;
import static uk.ac.manchester.cs.spinnaker.utils.ThreadUtils.sleep;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.UUID;

import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;

import com.xensource.xenapi.Connection;
import com.xensource.xenapi.SR;
import com.xensource.xenapi.Types.VmPowerState;
import com.xensource.xenapi.Types.XenAPIException;
import com.xensource.xenapi.VBD;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VM;

public class XenVMExecuterFactory implements JobExecuterFactory {
	/** Bytes in a gigabyte. Well, a gibibyte, but that's a nasty word. */
	private static final long GB = 1024L * 1024L * 1024L;
	/** Time (in ms) between checks to see if a VM is running. */
	private static final int VM_POLL_INTERVAL = 10000;

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
		this.xenServerUrl = requireNonNull(xenServerUrl);
		this.username = requireNonNull(username);
		this.password = requireNonNull(password);
		this.templateLabel = requireNonNull(templateLabel);
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
		requireNonNull(manager);
		requireNonNull(baseUrl);
		waitToClaimVM();

		try {
			return new Executer(manager, baseUrl);
		} catch (Exception e) {
			throw new IOException("Error creating VM", e);
		}
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

	protected Connection getConnection() throws XenAPIException, XmlRpcException {
		Connection conn = new Connection(xenServerUrl);
		loginWithPassword(conn, username, password);
		return conn;
	}

	protected void shutDownConnection(Connection conn) throws XenAPIException,
			XmlRpcException {
		logout(conn);
	}

	class Executer implements JobExecuter, Runnable {
		// Parameters from constructor
		private final JobManager jobManager;
		private final String uuid;
		private final URL jobProcessManagerUrl;
		private final String args;

		// Internal entities
		private VM clonedVm;
		private VBD disk;
		private VDI vdi;
		private VDI extraVdi;
		private VBD extraDisk;

		Executer(JobManager jobManager, URL baseUrl) throws XmlRpcException,
				IOException {
			this.jobManager = jobManager;
			uuid = UUID.randomUUID().toString();
			jobProcessManagerUrl = new URL(baseUrl, "job/"
					+ JOB_PROCESS_MANAGER_ZIP);

			StringBuilder args = new StringBuilder("-jar ");
			args.append(JOB_PROCESS_MANAGER_JAR);
			args.append(" --serverUrl ");
			args.append(baseUrl);
			args.append(" --executerId ");
			args.append(uuid);
			if (deleteOnExit)
				args.append(" --deleteOnExit");
			if (liveUploadOutput)
				args.append(" --liveUploadOutput");
			if (requestSpiNNakerMachine)
				args.append(" --requestMachine");
			this.args = args.toString();
		}

		@Override
		public String getExecuterId() {
			return uuid;
		}

		@Override
		public void startExecuter() {
			new Thread(threadGroup, this, "Executer:" + uuid).start();
		}

		private synchronized Connection createVm() throws XmlRpcException, IOException {
			Connection conn = getConnection();

			clonedVm = getVirtualMachine(conn);
			disk = getVirtualBlockDevice(conn, clonedVm);

			vdi = disk.getVDI(conn);
			String diskLabel = vdi.getNameLabel(conn);
			vdi.setNameLabel(conn, diskLabel + "_" + uuid + "_base");
			SR storageRepository = vdi.getSR(conn);
			extraVdi = createVDI(conn, diskLabel, storageRepository);
			extraDisk = createVBD(conn);

			clonedVm.addToXenstoreData(conn, "vm-data/nmpiurl",
					jobProcessManagerUrl.toString());
			clonedVm.addToXenstoreData(conn, "vm-data/nmpifile",
					JOB_PROCESS_MANAGER_ZIP);
			clonedVm.addToXenstoreData(conn, "vm-data/nmpiargs", args);
			if (shutdownOnExit)
				clonedVm.addToXenstoreData(conn, "vm-data/shutdown", "true");
			clonedVm.start(conn, false, true);
			return conn;
		}

		private VM getVirtualMachine(Connection conn) throws XmlRpcException,
				IOException {
			Set<VM> vmsWithLabel = VM.getByNameLabel(conn, templateLabel);
			if (vmsWithLabel.isEmpty())
				throw new IOException("No template with name " + templateLabel
						+ " was found");
			VM template = vmsWithLabel.iterator().next();
			return template.createClone(conn, templateLabel + "_" + uuid);
		}

		private VBD getVirtualBlockDevice(Connection conn, VM vm)
				throws XmlRpcException, IOException {
			Set<VBD> disks = vm.getVBDs(conn);
			if (disks.isEmpty())
				throw new IOException("No disks found on " + templateLabel);
			return disks.iterator().next();
		}
		
		private VDI createVDI(Connection conn, String diskLabel,
				SR storageRepository) throws XenAPIException, XmlRpcException {
			VDI.Record vdiRecord = new VDI.Record();
			vdiRecord.nameLabel = diskLabel + "_" + uuid + "_storage";
			vdiRecord.type = USER;
			vdiRecord.SR = storageRepository;
			vdiRecord.virtualSize = defaultDiskSizeInGbs * GB;
			return VDI.create(conn, vdiRecord);
		}

		private VBD createVBD(Connection conn) throws XenAPIException,
				XmlRpcException {
			VBD.Record vbdRecord = new VBD.Record();
			vbdRecord.VM = clonedVm;
			vbdRecord.VDI = extraVdi;
			vbdRecord.userdevice = "1";
			vbdRecord.mode = RW;
			vbdRecord.type = DISK;
			return VBD.create(conn, vbdRecord);
		}

		private synchronized void deleteVm(Connection conn)
				throws XenAPIException, XmlRpcException {
			if (conn == null)
				return;
			if (disk != null)
				disk.destroy(conn);
			if (extraDisk != null)
				extraDisk.destroy(conn);
			if (vdi != null)
				vdi.destroy(conn);
			if (extraVdi != null)
				extraVdi.destroy(conn);
			if (clonedVm != null)
				clonedVm.destroy(conn);
		}

		@Override
		public void run() {
			Connection conn = null;
			try {
				try {
					conn = createVm();
				} catch (Throwable e) {
					logger.error("Error setting up VM", e);
					jobManager.setExecutorExited(uuid, e.getMessage());
					return;
				}

				try {
					waitWhileVMRunning(conn);
				} catch (Exception e) {
					logger.error("Could not get VM power state, assuming off",
							e);
				} finally {
					jobManager.setExecutorExited(uuid, null);
				}

				try {
					if (deleteOnExit)
						deleteVm(conn);
				} catch (Throwable e) {
					logger.error("Error deleting VM");
				}
			} finally {
				try {
					if (conn != null)
						shutDownConnection(conn);
				} catch (XenAPIException | XmlRpcException e) {
					logger.error("problem when closing connection; "
							+ "resource potentially leaked", e);
				}
				executorFinished();
			}
		}

		private void waitWhileVMRunning(Connection conn)
				throws XenAPIException, XmlRpcException {
			VmPowerState powerState;
			do {
				sleep(VM_POLL_INTERVAL);
				powerState = clonedVm.getPowerState(conn);
				logger.debug("VM for " + uuid + " is in state " + powerState);
			} while (powerState != HALTED);
		}
	}
}
