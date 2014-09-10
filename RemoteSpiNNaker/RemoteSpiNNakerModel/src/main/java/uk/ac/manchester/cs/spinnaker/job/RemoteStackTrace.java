package uk.ac.manchester.cs.spinnaker.job;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a stack trace provided remotely
 */
public class RemoteStackTrace {

	private List<RemoteStackTraceElement> elements =
			new ArrayList<RemoteStackTraceElement>();

	public RemoteStackTrace() {

		// Does Nothing
	}

	public RemoteStackTrace(List<RemoteStackTraceElement> elements) {
		this.elements = elements;
	}

	public List<RemoteStackTraceElement> getElements() {
		return elements;
	}

	public void setElements(List<RemoteStackTraceElement> elements) {
		this.elements = elements;
	}

}
