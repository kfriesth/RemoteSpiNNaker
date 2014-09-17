package uk.ac.manchester.cs.spinnaker.nmpi.rest;

import uk.ac.manchester.cs.spinnaker.nmpi.model.Job;
import uk.ac.manchester.cs.spinnaker.nmpi.model.QueueEmpty;
import uk.ac.manchester.cs.spinnaker.nmpi.model.QueueNextResponse;

public class QueueResponseDeserialiser
        extends PropertyBasedDeserialiser<QueueNextResponse>{

	private static final long serialVersionUID = 1L;

	public QueueResponseDeserialiser() {
		super(QueueNextResponse.class);
		register("id", Job.class);
		register("warning", QueueEmpty.class);
	}
}
