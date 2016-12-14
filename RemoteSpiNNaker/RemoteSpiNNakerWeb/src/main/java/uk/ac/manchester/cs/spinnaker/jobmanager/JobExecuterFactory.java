package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.IOException;
import java.net.URL;

/**
 * A factory for creating job executers
 *
 * @see LocalJobExecuterFactory
 * @see XenVMExecuterFactory
 */
public interface JobExecuterFactory {
	/**
	 * Creates a new JobExecuter
	 *
	 * @param baseUrl
	 *            The URL of the manager
	 * @return The new executer
	 * @throws IOException
	 *             If there is an error creating the executer
	 */
	JobExecuter createJobExecuter(URL baseUrl) throws IOException;

	JobExecuter getJobExecuter(String executerId);
}
