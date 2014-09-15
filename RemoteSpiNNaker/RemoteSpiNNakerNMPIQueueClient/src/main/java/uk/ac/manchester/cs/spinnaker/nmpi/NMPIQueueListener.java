package uk.ac.manchester.cs.spinnaker.nmpi;

import java.io.IOException;
import java.util.List;

/**
 * An interface for things that listen for new jobs
 */
public interface NMPIQueueListener {

	void addJob(int id, String experimentDescription,  List<String> inputData,
			String hardwareConfig) throws IOException;
}
