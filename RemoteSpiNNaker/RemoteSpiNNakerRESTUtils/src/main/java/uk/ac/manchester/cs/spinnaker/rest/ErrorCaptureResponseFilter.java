package uk.ac.manchester.cs.spinnaker.rest;

import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.output.WriterOutputStream;
import org.slf4j.Logger;

// Only public because of the annotation
@Provider
public class ErrorCaptureResponseFilter implements ClientResponseFilter {
	private CustomJacksonJsonProvider provider = new CustomJacksonJsonProvider();
	private static final Logger log = getLogger(ErrorCaptureResponseFilter.class);
	public volatile boolean writeToLog = true;

	private static final String INDENT = "    ";// 4 spaces
	private static final String IND2 = INDENT + INDENT;

	@Override
	public void filter(ClientRequestContext requestContext,
			ClientResponseContext responseContext) throws IOException {
		if (!writeToLog)
			return;
		Family family = responseContext.getStatusInfo().getFamily();
		if ((family == CLIENT_ERROR) || (family == SERVER_ERROR)) {
			log.trace("Error when sending request:");
			log.trace(INDENT + "Headers:");
			MultivaluedMap<String, String> headers = requestContext
					.getStringHeaders();
			for (String headerName : headers.keySet())
				for (String headerValue : headers.get(headerName))
					log.trace(IND2 + headerName + ": " + headerValue);

			log.trace(INDENT + "Entity:");
			log.trace(IND2 + requestContext.getEntity());

			String json = getRequestAsJSON(requestContext);
			if (json != null) {
				log.trace(INDENT + "JSON version:");
				log.trace(IND2 + json);
			}
		}
	}

	private String getRequestAsJSON(ClientRequestContext requestContext) {
		try {
			StringWriter jsonWriter = new StringWriter();
			try (OutputStream jsonOutput = new WriterOutputStream(jsonWriter,
					"UTF-8")) {
				provider.writeTo(requestContext.getEntity(),
						requestContext.getEntityClass(),
						requestContext.getEntityType(),
						requestContext.getEntityAnnotations(),
						requestContext.getMediaType(),
						requestContext.getHeaders(), jsonOutput);
			}
			return jsonWriter.toString();
		} catch (Exception e) {
			log.trace("problem when converting request to JSON", e);
			return null;
		}
	}
}
