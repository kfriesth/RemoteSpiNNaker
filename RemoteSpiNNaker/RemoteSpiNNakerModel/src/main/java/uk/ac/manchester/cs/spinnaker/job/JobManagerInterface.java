package uk.ac.manchester.cs.spinnaker.job;

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

@Path("/job")
public interface JobManagerInterface {

    public static final String JOB_PROCESS_MANAGER_ZIP =
            "RemoteSpiNNakerJobProcessManager.zip";

    @GET
    @Path("next")
    @Produces(MediaType.APPLICATION_JSON)
    public Job getNextJob(@QueryParam("executerId") String executerId);

    @GET
    @Path("{id}/machine/max")
    @Produces(MediaType.APPLICATION_JSON)
    public SpinnakerMachine getLargestJobMachine(
        @PathParam("id") int id,
        @QueryParam("runTime") @DefaultValue("-1") double runTime);

    @GET
    @Path("{id}/machine")
    @Produces(MediaType.APPLICATION_JSON)
    public SpinnakerMachine getJobMachine(
        @PathParam("id") int id,
        @QueryParam("nChips") @DefaultValue("-1") int nChips,
        @QueryParam("runTime") @DefaultValue("-1") double runTime);

    @GET
    @Path("{id}/machine/checkLease")
    @Produces(MediaType.APPLICATION_JSON)
    public JobMachineAllocated checkMachineLease(
        @PathParam("id") int id,
        @QueryParam("waitTime") @DefaultValue("10000") int waitTime);

    @GET
    @Path("{id}/machine/extendLease")
    public void extendJobMachineLease(
        @PathParam("id") int id,
        @QueryParam("runTime") @DefaultValue("-1") double runTime);

    @POST
    @Path("{id}/log")
    @Consumes("text/plain")
    public void appendLog(@PathParam("id") int id, String logToAppend);

    @POST
    @Path("{id}/provenance")
    public void addProvenance(
        @PathParam("id") int id, @QueryParam("name") String name,
        @QueryParam("value") String value);

    @POST
    @Path("{projectId}/{id}/addoutput")
    @Consumes("application/octet-stream")
    public void addOutput(
        @PathParam("projectId") String projectId, @PathParam("id") int id,
        @QueryParam("outputFilename") String output, InputStream input);

    @POST
    @Path("{projectId}/{id}/finished")
    @Consumes("text/plain")
    public void setJobFinished(
        @PathParam("projectId") String projectId,
        @PathParam("id") int id, String logToAppend,
        @QueryParam("baseFilename") String baseFilename,
        @QueryParam("outputFilename") List<String> outputs);

    @POST
    @Path("{projectId}/{id}/error")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJobError(
        @PathParam("projectId") String projectId, @PathParam("id") int id,
        @QueryParam("error") String error,
        @QueryParam("logToAppend") String logToAppend,
        @QueryParam("baseFilename") String baseFilename,
        @QueryParam("outputFilename") List<String> outputs,
        RemoteStackTrace stackTrace);

    @GET
    @Path(JOB_PROCESS_MANAGER_ZIP)
    @Produces("application/zip")
    public Response getJobProcessManager();
}
