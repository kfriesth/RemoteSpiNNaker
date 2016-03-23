package uk.ac.manchester.cs.spinnaker.collab.rest;

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import uk.ac.manchester.cs.spinnaker.collab.model.DocEntity;
import uk.ac.manchester.cs.spinnaker.collab.model.DocEntityReturn;

@Path("/document/v0/api")
public interface DocumentRestService {

    @Path("/project/")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<DocEntityReturn> getAllProjects(
        @QueryParam("filter") String filter);

    @Path("/entity/")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public DocEntityReturn getEntityByPath(
        @QueryParam("path") String path);

    @Path("/entity/{uuid}/")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public DocEntityReturn getEntityByUUID(
        @PathParam("uuid") String uuid);

    @Path("/folder/")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public DocEntityReturn createFolder(DocEntity folder);

    @Path("/file/")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public DocEntityReturn createFile(DocEntity file);

    @Path("/file/{uuid}/content/upload")
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public DocEntityReturn uploadFile(String uuid, InputStream content);

    @Path("/file/{uuid}/content/download")
    @GET
    @Produces(MediaType.MEDIA_TYPE_WILDCARD)
    public Response downloadFile(String uuid);
}
