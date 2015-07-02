package uk.ac.manchester.cs.spinnaker.job;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("job")
public interface JobManagerInterface {

    @POST
    @Path("{id}/log")
    @Consumes("text/plain")
    public void appendLog(@PathParam("id") int id, String logToAppend);

    @POST
    @Path("{id}/finished")
    @Consumes("text/plain")
    public void setJobFinished(@PathParam("id") int id, String logToAppend,
            @QueryParam("outputFilename") List<String> outputs);

    @POST
    @Path("{id}/error")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJobError(@PathParam("id") int id,
            @QueryParam("error") String error,
            @QueryParam("logToAppend") String logToAppend,
            RemoteStackTrace stackTrace);
}
