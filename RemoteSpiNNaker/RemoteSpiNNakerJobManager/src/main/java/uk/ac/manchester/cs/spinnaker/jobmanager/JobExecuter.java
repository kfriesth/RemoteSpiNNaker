package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.IOException;

/**
 * Executes jobs in an external process
 *
 */
public interface JobExecuter {

    /**
    * Starts the external job
    * @throws IOException If there is an error starting the job
    */
    public void start() throws IOException;
}
