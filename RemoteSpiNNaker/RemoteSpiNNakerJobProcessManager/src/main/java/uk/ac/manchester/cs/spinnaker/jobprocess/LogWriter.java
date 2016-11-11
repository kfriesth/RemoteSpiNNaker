package uk.ac.manchester.cs.spinnaker.jobprocess;

/**
 * Something that logs can be written to.
 */
public interface LogWriter {
	/**
	 * Appends a line to a log.
	 * 
	 * @param log
	 *            The line to append. Should include the terminating newline.
	 */
	void append(String log);
}
