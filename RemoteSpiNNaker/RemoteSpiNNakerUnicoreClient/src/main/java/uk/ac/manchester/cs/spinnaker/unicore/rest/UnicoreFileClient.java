package uk.ac.manchester.cs.spinnaker.unicore.rest;

import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/storages")
public interface UnicoreFileClient {

    @PUT
    @Path("{id}/files/{filePath}")
    @Consumes("application/octet-stream")
    public void upload(
        @PathParam("id") String id, @PathParam("filePath") String filePath,
        InputStream input);
}
