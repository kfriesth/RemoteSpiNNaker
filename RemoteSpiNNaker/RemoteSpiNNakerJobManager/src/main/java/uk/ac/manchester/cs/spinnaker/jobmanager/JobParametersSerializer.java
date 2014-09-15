package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.IOException;
import java.util.LinkedHashMap;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.JobParametersTypeName;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.MapType;

public class JobParametersSerializer extends StdSerializer<JobParameters> {

	private String typeField = null;

	public JobParametersSerializer(String typeField) {
		super(JobParameters.class);

		this.typeField = typeField;
	}

	@Override
	public void serialize(JobParameters value, JsonGenerator jgen,
			SerializerProvider provider) throws IOException,
			JsonGenerationException {
		ObjectMapper mapper = new ObjectMapper();
		MapType mapType = mapper.getTypeFactory().constructMapType(
				LinkedHashMap.class, String.class, Object.class);
		LinkedHashMap<String, Object> map = mapper.convertValue(value, mapType);
		JobParametersTypeName typeNameAnnotation =
				value.getClass().getAnnotation(JobParametersTypeName.class);
		if (typeNameAnnotation == null) {
			throw new JsonGenerationException("Type " + value.getClass()
				+ " is not annotated with JobParametersTypeName and so"
				+ " cannot be serialized");
		}
		String typeName = typeNameAnnotation.value();
		map.put(typeField, typeName);

		jgen.writeStartObject();
		for (String key : map.keySet()) {
			Object item = map.get(key);
			jgen.writeObjectField(key, item);
		}
		jgen.writeEndObject();
	}
}
