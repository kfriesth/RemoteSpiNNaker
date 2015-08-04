package uk.ac.manchester.cs.spinnaker.nmpi.rest;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

@Provider
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.WILDCARD)
public class NMPIJacksonJsonProvider extends JacksonJsonProvider {

    private Set<ObjectMapper> registeredMappers = new HashSet<ObjectMapper>();

    private SimpleModule module = new SimpleModule();

    private JodaModule jodaModule = new JodaModule();

    public <T> void addDeserialiser(Class<T> type,
            StdDeserializer<T> deserialiser) {
        module.addDeserializer(type, deserialiser);
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType,
            Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, String> httpHeaders,
            InputStream entityStream)
            throws IOException {
        ObjectMapper mapper = locateMapper(type, mediaType);
        if (!registeredMappers.contains(mapper)) {
            mapper.registerModule(module);
            mapper.setPropertyNamingStrategy(
                    PropertyNamingStrategy
                        .CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
            mapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.registerModule(jodaModule);
            registeredMappers.add(mapper);
        }
        return super.readFrom(type, genericType, annotations, mediaType, httpHeaders,
                entityStream);
    }
}
