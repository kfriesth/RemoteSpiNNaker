package uk.ac.manchester.cs.spinnaker.rest;

import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;

import java.io.IOException;
import java.io.StringWriter;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.output.WriterOutputStream;

@Provider
public class ErrorCaptureResponseFilter implements ClientResponseFilter {
	private CustomJacksonJsonProvider provider = new CustomJacksonJsonProvider();

	private static void log(String s) {
		// TODO log this better
		System.err.println(s);
	}

	@Override
	public void filter(ClientRequestContext requestContext,
			ClientResponseContext responseContext) throws IOException {
		Family family = responseContext.getStatusInfo().getFamily();
		if ((family == CLIENT_ERROR) || (family == SERVER_ERROR)) {
			log("Error when sending request:");
			log("    Headers:");
			MultivaluedMap<String, String> headers = requestContext
					.getStringHeaders();
			for (String headerName : headers.keySet())
				for (String headerValue : headers.get(headerName))
					log("        " + headerName + ": " + headerValue);

			log("    Entity:");
			log("        " + requestContext.getEntity());

			String json = getRequestAsJSON(requestContext);
			if (json != null) {
				log("    JSON version:");
				log("        " + json);
			}
		}
	}

	private String getRequestAsJSON(ClientRequestContext requestContext) {
		try {
			StringWriter jsonWriter = new StringWriter();
			WriterOutputStream jsonOutput = new WriterOutputStream(
					jsonWriter);
			provider.writeTo(requestContext.getEntity(),
					requestContext.getEntityClass(),
					requestContext.getEntityType(),
					requestContext.getEntityAnnotations(),
					requestContext.getMediaType(),
					requestContext.getHeaders(), jsonOutput);
			jsonOutput.close();
			jsonWriter.close();
			return jsonWriter.toString();
		} catch (Exception e) {
			// TODO log this better
			e.printStackTrace();
			return null;
		}
	}
}
