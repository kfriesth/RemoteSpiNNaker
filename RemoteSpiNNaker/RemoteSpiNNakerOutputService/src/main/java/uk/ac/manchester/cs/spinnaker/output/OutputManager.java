package uk.ac.manchester.cs.spinnaker.output;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;

@Path("/output")
public interface OutputManager {

    /**
     * Adds outputs to be hosted for a given id, returning a matching list of
     * URLs on which the files are hosted
     *
     * @param projectId The id of the project
     * @param id The id of the job
     * @param rootFile The root directory containing all the files
     * @param files The files to add
     * @return A list of DataItem instances for adding to the job
     * @throws IOException
     */
    public List<DataItem> addOutputs(
        String projectId, int id, File rootFile, List<File> outputs)
        throws IOException;

    /**
    * Gets a results file
    * @param id The id of the job which produced the file
    * @param filename The name of the file
    * @return A response containing the file, or a "NOT FOUND" response if the
    *         file does not exist
    */
    // TODO: Enable authentication based on collab id
    //@PreAuthorize("@collabSecurityService.canRead(#projectId)")
    @GET
    @Path("{projectId}/{id}/{filename:.*}")
    @Produces(MediaType.MEDIA_TYPE_WILDCARD)
    public Response getResultFile(
        @PathParam("projectId") String projectId,
        @PathParam("id") int id,
        @PathParam("filename") String filename,
        @QueryParam("download") @DefaultValue("true") boolean download);

    @GET
    @Path("{id}/{filename:.*}")
    @Produces(MediaType.MEDIA_TYPE_WILDCARD)
    public Response getResultFile(
        @PathParam("id") int id,
        @PathParam("filename") String filename,
        @QueryParam("download") @DefaultValue("true") boolean download);

    @POST
    @Path("{projectId}/{id}/uploadToHPC")
    // TODO: Enable authentication based on collab id
    //@PreAuthorize("@collabSecurityService.canWrite(#projectId)")
    public Response uploadResultsToHPCServer(
        @PathParam("projectId") String projectId,
        @PathParam("id") int id, @QueryParam("url") String serverUrl,
        @QueryParam("storageId") String storageId,
        @QueryParam("filePath") String filePath,
        @QueryParam("userId") String userId,
        @QueryParam("token") String token);

}
