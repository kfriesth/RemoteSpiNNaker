package uk.ac.manchester.cs.spinnaker.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import uk.ac.manchester.cs.spinnaker.model.CollabContext;
import uk.ac.manchester.cs.spinnaker.model.CollabPermissions;

@Path("/collab/v0")
public interface CollabRestService {
	@GET
	@Path("/collab/context/{contextId}")
	@Produces(APPLICATION_JSON)
	CollabContext getCollabContext(@PathParam("contextId") String contextId);

	@GET
	@Path("/collab/{id}/permissions")
	@Produces(APPLICATION_JSON)
	CollabPermissions getCollabPermissions(@PathParam("id") int id);
}
