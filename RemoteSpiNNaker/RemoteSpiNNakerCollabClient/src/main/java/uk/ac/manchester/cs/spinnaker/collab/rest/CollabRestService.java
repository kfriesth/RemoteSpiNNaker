package uk.ac.manchester.cs.spinnaker.collab.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import uk.ac.manchester.cs.spinnaker.collab.model.CollabContext;
import uk.ac.manchester.cs.spinnaker.collab.model.CollabPermissions;

@Path("/collab/v0")
public interface CollabRestService {

    @GET
    @Path("/collab/context/{contextId}/")
    @Produces(MediaType.APPLICATION_JSON)
    public CollabContext getCollabContex(
        @PathParam("contextId") String contextId);

    @GET
    @Path("/collab/{id}/permissions/")
    @Produces(MediaType.APPLICATION_JSON)
    public CollabPermissions getCollabPermissions(@PathParam("id") int id);

}
