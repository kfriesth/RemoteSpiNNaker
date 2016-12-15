package uk.ac.manchester.cs.spinnaker.jobmanager;

/**
 * Executes jobs in an external process
 *
 * @see LocalJobExecuterFactory.Executer
 * @see XenVMExecuterFactory.NewExecuter
 */
public interface JobExecuter {
	/**
	 * Gets the id of the executer
	 * 
	 * @return The id
	 */
	String getExecuterId();

	/**
	 * Starts the external job. Expected to launch a thread and/or process
	 * immediately.
	 */
	void startExecuter();
}
