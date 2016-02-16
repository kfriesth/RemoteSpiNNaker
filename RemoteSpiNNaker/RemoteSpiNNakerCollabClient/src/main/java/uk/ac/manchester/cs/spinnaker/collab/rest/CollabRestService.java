package uk.ac.manchester.cs.spinnaker.collab.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import uk.ac.manchester.cs.spinnaker.collab.model.CollabContext;
import uk.ac.manchester.cs.spinnaker.collab.model.CollabPermissions;

@Path("/collab/v0")
public interface CollabRestService {

    @Path("/collab/context/{contextId}")
    @GET
    @Produces("application/json")
    public CollabContext getCollabContex(
        @PathParam("contextId") String contextId);

    @Path("/collab/{id}/permissions")
    @GET
    @Produces("applcation/json")
    public CollabPermissions getCollabPermissions(@PathParam("id") int id);

}
