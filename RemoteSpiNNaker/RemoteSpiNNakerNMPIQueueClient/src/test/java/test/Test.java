package test;

import java.io.File;

import uk.ac.manchester.cs.spinnaker.nmpi.model.QueueNextResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Test {

	public static void main(String[] args) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		QueueNextResponse response = mapper.readValue(new File("test.json"),
				QueueNextResponse.class);
		System.err.println(response.getClass());
	}

}
