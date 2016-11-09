package uk.ac.manchester.cs.spinnaker.job;

/**
 * The possible statuses of a process.
 */
public enum Status {
	/**
	 * Indicates that the process is currently running.
	 */
	Running,
	/**
	 * Indicates that the process has completed successfully.
	 */
	Finished,
	/**
	 * Indicates that the process has stopped with an error.
	 */
	Error
}
