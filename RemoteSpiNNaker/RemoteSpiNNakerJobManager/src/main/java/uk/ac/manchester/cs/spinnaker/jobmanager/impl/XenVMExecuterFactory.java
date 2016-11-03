package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import static uk.ac.manchester.cs.spinnaker.job.JobManagerInterface.JOB_PROCESS_MANAGER_ZIP;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;

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
	private final Integer maxNVirtualMachines;

	private int nVirtualMachines = 0;
	private Log logger = LogFactory.getLog(getClass());

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
		this.maxNVirtualMachines = new Integer(maxNVirtualMachines);
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
		args.append(JobManager.JOB_PROCESS_MANAGER_JAR);
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

		return new XenVMExecuter(manager, this, uuid, xenServerUrl, username,
				password, templateLabel, defaultDiskSizeInGbs,
				jobProcessManagerUrl.toString(),
				JobManager.JOB_PROCESS_MANAGER_ZIP, args.toString(),
				deleteOnExit, shutdownOnExit);
	}

	private void waitToClaimVM() {
		synchronized (maxNVirtualMachines) {
			logger.debug(nVirtualMachines + " of " + maxNVirtualMachines
					+ " in use");
			while (nVirtualMachines >= maxNVirtualMachines) {
				logger.debug("Waiting for a VM to become available ("
						+ nVirtualMachines + " of " + maxNVirtualMachines
						+ " in use)");
				try {
					maxNVirtualMachines.wait();
				} catch (InterruptedException e) {
					// Does Nothing
				}
			}
			nVirtualMachines++;
		}
	}

	protected void executorFinished() {
		synchronized (maxNVirtualMachines) {
			nVirtualMachines--;
			logger.debug(nVirtualMachines + " of " + maxNVirtualMachines
					+ " now in use");
			maxNVirtualMachines.notifyAll();
		}
	}
}
