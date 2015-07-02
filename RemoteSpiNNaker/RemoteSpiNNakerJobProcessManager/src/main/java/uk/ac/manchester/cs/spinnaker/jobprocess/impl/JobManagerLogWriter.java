package uk.ac.manchester.cs.spinnaker.jobprocess.impl;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.jobprocess.LogWriter;

/**
 * A log writer that writes to the job manager
 */
public class JobManagerLogWriter implements LogWriter {

    private JobManagerInterface jobManager = null;

    private int id = 0;

    private String cached = "";

    public JobManagerLogWriter(JobManagerInterface jobManager, int id) {
        this.jobManager = jobManager;
        this.id = id;
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
            //sendTimer.restart();
        }
    }

    public String getLog() {
        return cached;
    }

}
