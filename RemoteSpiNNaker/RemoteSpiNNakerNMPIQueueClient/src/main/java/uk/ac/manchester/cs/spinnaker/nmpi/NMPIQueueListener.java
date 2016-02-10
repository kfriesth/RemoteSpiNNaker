package uk.ac.manchester.cs.spinnaker.nmpi;

import java.io.IOException;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;

/**
 * An interface for things that listen for new jobs
 */
public interface NMPIQueueListener {

    void addJob(Job job, boolean deleteJobOnExit) throws IOException;
}
