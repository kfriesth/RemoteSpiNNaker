package uk.ac.manchester.cs.spinnaker.jobprocess;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.JobParametersTypeName;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JobParametersDeserializer extends StdDeserializer<JobParameters> {

	private static final long serialVersionUID = 1L;

	private Map<String, Class<? extends JobParameters>> typeMap =
			new HashMap<String, Class<? extends JobParameters>>();

	private String typeField = null;

	public JobParametersDeserializer(String typeField,
			Collection<Class<? extends JobParameters>> types) {
		super(JobParameters.class);

		this.typeField = typeField;

		for (Class<? extends JobParameters> type : types) {
			JobParametersTypeName typeNameAnnotation =
					type.getAnnotation(JobParametersTypeName.class);
			if (typeNameAnnotation == null) {
				throw new RuntimeException("Type " + type
					+ " is not annotated with JobParametersTypeName and so"
					+ " cannot be deserialized");
			}
			String typeName = typeNameAnnotation.value();
			if (typeMap.containsKey(typeName)) {
				throw new RuntimeException("A type with type name " + typeName
					+ " has already been mapped (existing = "
						+ typeMap.get(typeName) + ") when adding type " + type);
			}

			typeMap.put(typeName, type);
		}
	}

	@Override
	public JobParameters deserialize(JsonParser parser,
			DeserializationContext context) throws IOException,
			JsonProcessingException {
		ObjectMapper mapper = (ObjectMapper) parser.getCodec();
	    ObjectNode root = (ObjectNode) mapper.readTree(parser);

	    JsonNode typeNode = root.remove(typeField);
	    if (typeNode == null) {
	    	throw new JsonMappingException(
	    			"Type field " + typeField + " not found in received object."
	    			+ " Ensure that the serialiser is configured to add this"
	    			+ " field to the serialised object.");
	    }
	    String typeName = typeNode.asText();
	    Class<? extends JobParameters> type = typeMap.get(typeName);
	    System.err.println("Deserialising " + type);

	    if (type == null) {
	    	throw new JsonMappingException("No type with name " + typeName
	    			+ " was found.  Ensure that the deserialiser has been"
	    			+ " configured with all required types");
	    }

	    return mapper.treeToValue(root, type);
	}
}
