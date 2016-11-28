package uk.ac.manchester.cs.spinnaker.machinemanager.responses;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

public class ReturnResponse implements Response {
	private String returnValue;

	public String getReturnValue() {
		return returnValue;
	}

	@JsonSetter("return")
	public void setReturnValue(JsonNode returnValue) {
		this.returnValue = returnValue.toString();
	}
}
