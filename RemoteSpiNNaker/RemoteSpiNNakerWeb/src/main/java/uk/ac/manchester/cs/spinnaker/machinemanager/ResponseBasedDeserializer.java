package uk.ac.manchester.cs.spinnaker.machinemanager;

import uk.ac.manchester.cs.spinnaker.machinemanager.responses.JobsChangedResponse;
import uk.ac.manchester.cs.spinnaker.machinemanager.responses.Response;
import uk.ac.manchester.cs.spinnaker.machinemanager.responses.ReturnResponse;
import uk.ac.manchester.cs.spinnaker.rest.utils.PropertyBasedDeserialiser;

public class ResponseBasedDeserializer extends
		PropertyBasedDeserialiser<Response> {
	private static final long serialVersionUID = 1L;

	public ResponseBasedDeserializer() {
		super(Response.class);
		register("jobs_changed", JobsChangedResponse.class);
		register("return", ReturnResponse.class);
	}
}
