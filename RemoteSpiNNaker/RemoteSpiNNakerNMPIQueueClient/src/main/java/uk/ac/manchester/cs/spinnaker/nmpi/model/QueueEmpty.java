package uk.ac.manchester.cs.spinnaker.nmpi.model;

/**
 * A message indicating that the queue is empty
 *
 */
public class QueueEmpty implements QueueNextResponse {

	private String warning = null;

	public String getWarning() {
		return warning;
	}

	public void setWarning(String warning) {
		this.warning = warning;
	}
}
