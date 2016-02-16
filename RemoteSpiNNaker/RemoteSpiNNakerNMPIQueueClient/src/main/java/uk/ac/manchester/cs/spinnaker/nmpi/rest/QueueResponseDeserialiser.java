package uk.ac.manchester.cs.spinnaker.nmpi.rest;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job.nmpi.QueueEmpty;
import uk.ac.manchester.cs.spinnaker.job.nmpi.QueueNextResponse;
import uk.ac.manchester.cs.spinnaker.rest.PropertyBasedDeserialiser;

public class QueueResponseDeserialiser
        extends PropertyBasedDeserialiser<QueueNextResponse>{

	private static final long serialVersionUID = 1L;

	public QueueResponseDeserialiser() {
		super(QueueNextResponse.class);
		register("id", Job.class);
		register("warning", QueueEmpty.class);
	}
}
