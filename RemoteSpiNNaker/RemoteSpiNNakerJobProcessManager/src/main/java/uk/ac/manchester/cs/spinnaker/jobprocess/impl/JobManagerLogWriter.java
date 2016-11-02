package uk.ac.manchester.cs.spinnaker.jobprocess.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.jobprocess.LogWriter;

/**
 * A log writer that writes to the job manager
 */
public class JobManagerLogWriter implements LogWriter {
	private static final int UPDATE_INTERVAL = 500;

	private final JobManagerInterface jobManager;
	private final int id;
	private final StringBuilder cached = new StringBuilder();
	private final Timer sendTimer;

	public JobManagerLogWriter(JobManagerInterface jobManager, int id,
			boolean updateLive) {
		this.jobManager = jobManager;
		this.id = id;

		if (updateLive)
			sendTimer = new Timer(UPDATE_INTERVAL, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					sendLog();
				}
			});
		else
			sendTimer = null;
	}

	private synchronized void sendLog() {
		if (cached.length() > 0) {
			System.err.println("Sending cached data to job manager");
			jobManager.appendLog(id, cached.toString());
			cached.setLength(0);
		}
	}

	@Override
	public void append(String log) {
		System.err.print("Process Output: " + log);
		synchronized (this) {
			cached.append(log);
			if (sendTimer != null)
				sendTimer.restart();
		}
	}

	public String getLog() {
		synchronized (this) {
			return cached.toString();
		}
	}

	public void stop() {
		if (sendTimer != null)
			sendTimer.stop();
	}
}
