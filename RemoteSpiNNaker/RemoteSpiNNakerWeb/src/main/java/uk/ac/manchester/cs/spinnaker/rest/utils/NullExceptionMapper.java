package uk.ac.manchester.cs.spinnaker.rest.utils;

import static javax.ws.rs.core.Response.status;
import static org.slf4j.LoggerFactory.getLogger;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;

/**
 * Convert NPEs into complaints about their most likely causes, bad inputs.
 * 
 * @author Donal Fellows
 */
@Provider
public class NullExceptionMapper implements
		ExceptionMapper<NullPointerException> {
	private Logger log = getLogger(getClass());

	@Override
	public Response toResponse(NullPointerException exception) {
		String msg = exception.getMessage();
		if (msg == null || msg.isEmpty())
			msg = "bad parameter";
		log.info("trapped exception in service method", exception);
		return status(400).entity(msg).build();
	}
}
