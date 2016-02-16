package uk.ac.manchester.cs.spinnaker.output;

import java.io.File;
import java.net.MalformedURLException;
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
     * @param id
     * @param files
     * @return
     * @throws MalformedURLException
     */
    @POST
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<DataItem> addOutputs(
            @PathParam("id") int id, @QueryParam("output") List<File> outputs)
            throws MalformedURLException;

    /**
    * Gets a results file
    * @param id The id of the job which produced the file
    * @param filename The name of the file
    * @return A response containing the file, or a "NOT FOUND" response if the
    *         file does not exist
    */
    // TODO: Enable authentication based on collab id
    //@PreAuthorize("@collabSecurityService.canRead(#collabId)")
    @GET
    @Path("{id}/{filename:.*}")
    @Produces(MediaType.MEDIA_TYPE_WILDCARD)
    public Response getResultFile(@PathParam("id") int id,
            @PathParam("filename") String filename,
            @QueryParam("download") @DefaultValue("true") boolean download);

    @POST
    @Path("{id}/uploadToHPC")
    public Response uploadResultsToHPCServer(
            @PathParam("id") int id, @QueryParam("url") String serverUrl,
            @QueryParam("storageId") String storageId,
            @QueryParam("filePath") String filePath,
            @QueryParam("userId") String userId,
            @QueryParam("token") String token);

}
