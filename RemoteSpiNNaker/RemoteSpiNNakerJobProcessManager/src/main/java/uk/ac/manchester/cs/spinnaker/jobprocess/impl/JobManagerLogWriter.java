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

    private JobManagerInterface jobManager = null;

    private int id = 0;

    private String cached = "";

    private Timer sendTimer = null;

    public JobManagerLogWriter(
            JobManagerInterface jobManager, int id, boolean updateLive) {
        this.jobManager = jobManager;
        this.id = id;

        if (updateLive) {
            sendTimer = new Timer(500, new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    synchronized (this) {
                        sendLog();
                    }
                }
            });
        }
    }

    public void sendLog() {
        synchronized (this) {
            if (cached != "") {
                System.err.println(
                        "Sending cached data to job manager");
                JobManagerLogWriter.this.jobManager.appendLog(
                        JobManagerLogWriter.this.id, cached);
                cached = "";
            }
        }
    }

    @Override
    public void append(String log) {
        synchronized (this) {
            System.err.println("Process Output: " + log);
            cached += log;
            if (sendTimer != null) {
                sendTimer.restart();
            }
        }
    }

    public String getLog() {
        synchronized (this) {
            return cached;
        }
    }

}
