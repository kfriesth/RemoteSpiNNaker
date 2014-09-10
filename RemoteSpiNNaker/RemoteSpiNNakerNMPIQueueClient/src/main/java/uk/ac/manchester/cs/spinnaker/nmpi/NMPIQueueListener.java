package uk.ac.manchester.cs.spinnaker.nmpi;

import java.util.List;

/**
 * An interface for things that listen for new jobs
 */
public interface NMPIQueueListener {

	void addJob(int id, List<String> inputData, String hardwareConfig);
}
