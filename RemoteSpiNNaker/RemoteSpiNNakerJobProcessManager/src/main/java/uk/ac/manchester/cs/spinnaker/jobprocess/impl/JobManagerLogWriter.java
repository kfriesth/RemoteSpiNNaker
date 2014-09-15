package uk.ac.manchester.cs.spinnaker.jobprocess.impl;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.jobprocess.LogWriter;

/**
 * A log writer that writes to the job manager
 */
public class JobManagerLogWriter implements LogWriter {

	private JobManagerInterface jobManager = null;

	private int id = 0;

	public JobManagerLogWriter(JobManagerInterface jobManager, int id) {
		this.jobManager = jobManager;
		this.id = id;
	}

	@Override
	public void append(String log) {
		jobManager.appendLog(id, log);
	}

}
