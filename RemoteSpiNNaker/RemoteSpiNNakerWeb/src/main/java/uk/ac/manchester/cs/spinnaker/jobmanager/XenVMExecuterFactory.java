package uk.ac.manchester.cs.spinnaker.jobmanager;

import static com.xensource.xenapi.Session.loginWithPassword;
import static com.xensource.xenapi.Session.logout;
import static com.xensource.xenapi.Types.VbdMode.RW;
import static com.xensource.xenapi.Types.VbdType.DISK;
import static com.xensource.xenapi.Types.VdiType.USER;
import static com.xensource.xenapi.Types.VmPowerState.HALTED;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Types.BadServerResponse;
import com.xensource.xenapi.Types.VmPowerState;
import com.xensource.xenapi.Types.XenAPIException;
import com.xensource.xenapi.VBD;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VM;

public class XenVMExecuterFactory extends AbstractJobExecuterFactory {
	/** Bytes in a gigabyte. Well, a gibibyte, but that's a nasty word. */
	private static final long GB = 1024L * 1024L * 1024L;
	/** Time (in ms) between checks to see if a VM is running. */
	private static final int VM_POLL_INTERVAL = 10000;

	private final Object lock = new Object();
	private final ThreadGroup threadGroup;
	private final Logger logger = getLogger(getClass());

	@Value("${xen.server.url}")
	private URL xenServerUrl;
    @Value("${xen.server.username}")
	private String username;
    @Value("${xen.server.password}")
	private String password;
    @Value("${xen.server.templateVm}")
	private String templateLabel;
    @Value("${deleteJobsOnExit}")
	private boolean deleteOnExit;
    @Value("${xen.server.shutdownOnExit}")
	private boolean shutdownOnExit;
    @Value("${liveUploadOutput}")
	private boolean liveUploadOutput;
    @Value("${requestSpiNNakerMachine}")
	private boolean requestSpiNNakerMachine;
    @Value("${xen.server.diskspaceInGbs}")
	private long defaultDiskSizeInGbs;
    @Value("${xen.server.maxVms}")
	private int maxNVirtualMachines;

    @Autowired
    private JobManager jobManager;
    @Autowired
    private JobStorage storage;
	private int nVirtualMachines = 0;

	public XenVMExecuterFactory() {
		this.threadGroup = new ThreadGroup("XenVM");
	}

	@Override
	protected JobExecuter makeExecuter(URL baseUrl) throws IOException {
		waitToClaimVM();

		try {
			return new Executer(baseUrl);
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

	@Override
	protected void executorFinished(JobExecuter executor) {
		synchronized (lock) {
			nVirtualMachines--;
			logger.debug(nVirtualMachines + " of " + maxNVirtualMachines
					+ " now in use");
			storage.removeXenVm(executor.getExecuterId());
			super.executorFinished(executor);
			lock.notifyAll();
		}
	}

	/** Taming the Xen API a bit. */
	class XenConnection implements AutoCloseable {
		private Connection conn;
		private String id;

		XenConnection(String id) throws XenAPIException, XmlRpcException {
			this.id = id;
			conn = new Connection(xenServerUrl);
			loginWithPassword(conn, username, password);
		}

		VM getVirtualMachine() throws XmlRpcException, IOException {
			Set<VM> vmsWithLabel = VM.getByNameLabel(conn, templateLabel);
			if (vmsWithLabel.isEmpty())
				throw new IOException("No template with name " + templateLabel
						+ " was found");
			VM template = vmsWithLabel.iterator().next();
			return template.createClone(conn, templateLabel + "_" + id);
		}

		VBD getVirtualBlockDevice(VM vm) throws XmlRpcException, IOException {
			Set<VBD> disks = vm.getVBDs(conn);
			if (disks.isEmpty())
				throw new IOException("No disks found on " + templateLabel);
			return disks.iterator().next();
		}

		String getLabel(VDI vdi, String suffix) throws XmlRpcException, XenAPIException {
			return vdi.getNameLabel(conn) + "_" + id + "_" + suffix;
		}

		VDI getBaseVDI(VBD disk) throws XmlRpcException, XenAPIException {
			VDI vdi = disk.getVDI(conn);
			vdi.setNameLabel(conn, getLabel(vdi, "base"));
			return vdi;
		}

		VDI createVDI(VDI baseVDI) throws XenAPIException, XmlRpcException {
			VDI.Record descriptor = new VDI.Record();
			descriptor.nameLabel = getLabel(baseVDI, "storage");
			descriptor.type = USER;
			descriptor.SR = baseVDI.getSR(conn);
			descriptor.virtualSize = defaultDiskSizeInGbs * GB;

			return VDI.create(conn, descriptor);
		}

		VBD createVBD(VM vm, VDI vdi) throws XenAPIException, XmlRpcException {
			VBD.Record descriptor = new VBD.Record();
			descriptor.VM = vm;
			descriptor.VDI = vdi;
			descriptor.userdevice = "1";
			descriptor.mode = RW;
			descriptor.type = DISK;

			return VBD.create(conn, descriptor);
		}

		void addData(VM vm, String key, Object value) throws XenAPIException,
				XmlRpcException {
			vm.addToXenstoreData(conn, key, value.toString());
		}

		void start(VM vm) throws XenAPIException, XmlRpcException {
			vm.start(conn, false, true);
		}

		void destroy(VM vm) throws XenAPIException, XmlRpcException {
			vm.destroy(conn);
		}

		void destroy(VBD vm) throws XenAPIException, XmlRpcException {
			vm.destroy(conn);
		}

		void destroy(VDI vm) throws XenAPIException, XmlRpcException {
			vm.destroy(conn);
		}

		VmPowerState getState(VM vm) throws XenAPIException, XmlRpcException {
			return vm.getPowerState(conn);
		}

		String getID(VM vm) throws XenAPIException, XmlRpcException {
			return vm.getUuid(conn);
		}

		String getID(VDI vdi) throws XenAPIException, XmlRpcException {
			return vdi.getUuid(conn);
		}

		String getID(VBD vbd) throws XenAPIException, XmlRpcException {
			return vbd.getUuid(conn);
		}

		@Override
		public void close() {
			try {
				logout(conn);
			} catch (XenAPIException | XmlRpcException e) {
				logger.error("problem when closing connection; "
						+ "resource potentially leaked", e);
			} finally {
				conn = null;
			}
		}
	}

	static class XenVMDescriptor {
		String vm;
		String disk1;
		String vdi1;
		String disk2;
		String vdi2;

		XenVMDescriptor() {
		}

		XenVMDescriptor(XenConnection conn, VM vm, VBD disk1, VDI ifc1,
				VBD disk2, VDI ifc2) throws XenAPIException, XmlRpcException {
			this.vm = conn.getID(vm);
			this.disk1 = conn.getID(disk1);
			this.vdi1 = conn.getID(ifc1);
			this.disk2 = conn.getID(disk2);
			this.vdi2 = conn.getID(ifc2);
		}
	}

	class Executer implements JobExecuter, Runnable {
		// Parameters from constructor
		private final String uuid;
		private final URL jobProcessManagerUrl;
		private final String args;

		// Internal entities
		private VM clonedVm;
		private VBD disk;
		private VDI vdi;
		private VDI extraVdi;
		private VBD extraDisk;

		Executer(URL baseUrl) throws XmlRpcException, IOException {
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
			new Thread(threadGroup, this, "Executer (" + uuid + ")").start();
		}

		synchronized XenConnection createVm() throws XmlRpcException, IOException {
			XenConnection conn = new XenConnection(uuid);
			clonedVm = conn.getVirtualMachine();
			disk = conn.getVirtualBlockDevice(clonedVm);
			vdi = conn.getBaseVDI(disk);
			extraVdi = conn.createVDI(vdi);
			extraDisk = conn.createVBD(clonedVm, extraVdi);
			conn.addData(clonedVm, "vm-data/nmpiurl", jobProcessManagerUrl);
			conn.addData(clonedVm, "vm-data/nmpifile", JOB_PROCESS_MANAGER_ZIP);
			conn.addData(clonedVm, "vm-data/nmpiargs", args);
			if (shutdownOnExit)
				conn.addData(clonedVm, "vm-data/shutdown", true);
			conn.start(clonedVm);
			storage.addXenVm(uuid, new XenVMDescriptor(conn, clonedVm, disk, vdi, extraDisk, extraVdi));
			return conn;
		}

		private synchronized void deleteVm(XenConnection conn)
				throws XenAPIException, XmlRpcException {
			if (conn == null)
				return;
			if (disk != null)
				conn.destroy(disk);
			if (extraDisk != null)
				conn.destroy(extraDisk);
			if (vdi != null)
				conn.destroy(vdi);
			if (extraVdi != null)
				conn.destroy(extraVdi);
			if (clonedVm != null)
				conn.destroy(clonedVm);
		}

		@Override
		public void run() {
			try (XenConnection conn = createVm()) {
				try {
					VmPowerState powerState;
					do {
						sleep(VM_POLL_INTERVAL);
						powerState = conn.getState(clonedVm);
						logger.debug("VM for " + uuid + " is in state " + powerState);
					} while (powerState != HALTED);
				} catch (Exception e) {
					logger.error("Could not get VM power state, assuming off",
							e);
				} finally {
					jobManager.setExecutorExited(uuid, null);
				}

				try {
					if (deleteOnExit)
						deleteVm(conn);
				} catch (Exception e) {
					logger.error("Error deleting VM");
				}
			} catch (Exception e) {
				logger.error("Error setting up VM", e);
				jobManager.setExecutorExited(uuid, e.getMessage());
			} finally {
				executorFinished(this);
			}
		}
	}
}
