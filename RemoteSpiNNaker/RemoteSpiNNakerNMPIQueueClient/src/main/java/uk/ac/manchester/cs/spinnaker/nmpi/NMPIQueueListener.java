package uk.ac.manchester.cs.spinnaker.nmpi;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * An interface for things that listen for new jobs
 */
public interface NMPIQueueListener {

    void addJob(int id, String experimentDescription,
            List<String> inputDataUrls, Map<String, Object> hardwareConfig,
            boolean deleteJobOnExit)
                    throws IOException;
}
