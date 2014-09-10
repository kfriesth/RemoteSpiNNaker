package uk.ac.manchester.cs.spinnaker.nmpi.rest;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

public class NMPIJacksonJsonProvider extends JacksonJsonProvider {

	private Set<ObjectMapper> registeredMappers = new HashSet<ObjectMapper>();

	private SimpleModule module = new SimpleModule();

	public <T> void addDeserialiser(Class<T> type,
			StdDeserializer<T> deserialiser) {
		module.addDeserializer(type, deserialiser);
	}

	@Override
	public void writeTo(Object value, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException {
        ObjectMapper mapper = locateMapper(type, mediaType);
        if (!registeredMappers.contains(mapper)) {
            mapper.registerModule(module);
            registeredMappers.add(mapper);
        }

        super.writeTo(value, type, genericType, annotations, mediaType,
        		httpHeaders, entityStream);
    }

}
