package uk.ac.manchester.cs.spinnaker.rest;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.output.WriterOutputStream;

@Provider
public class ErrorCaptureResponseFilter implements ClientResponseFilter {

    private CustomJacksonJsonProvider provider =
        new CustomJacksonJsonProvider();

    @Override
    public void filter(ClientRequestContext requestContext,
            ClientResponseContext responseContext)
            throws IOException {

        Family family = responseContext.getStatusInfo().getFamily();
        if ((family == Family.CLIENT_ERROR)
                || (family == Family.SERVER_ERROR)) {
            System.err.println("Error when sending request:");
            System.err.println("    Headers:");
            MultivaluedMap<String, String> headers =
                    requestContext.getStringHeaders();
            for (String headerName : headers.keySet()) {
                List<String> headerValues = headers.get(headerName);
                for (String headerValue : headerValues) {
                    System.err.println("        " + headerName + ": "
                        + headerValue);
                }
            }
            String json = null;
            try {
                StringWriter jsonWriter = new StringWriter();
                WriterOutputStream jsonOutput = new WriterOutputStream(
                    jsonWriter);
                provider.writeTo(
                    requestContext.getEntity(), requestContext.getEntityClass(),
                    requestContext.getEntityType(),
                    requestContext.getEntityAnnotations(),
                    requestContext.getMediaType(), requestContext.getHeaders(),
                    jsonOutput);
                jsonOutput.close();
                jsonWriter.close();
                json = jsonWriter.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.err.println("    Entity:");
            System.err.println("        " + requestContext.getEntity());
            if (json != null) {
                System.err.println("    JSON version:");
                System.err.println("        " + json);
            }
        }
    }

}
