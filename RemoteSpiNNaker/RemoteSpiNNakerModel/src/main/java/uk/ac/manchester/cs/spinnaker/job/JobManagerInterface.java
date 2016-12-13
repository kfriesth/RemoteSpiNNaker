package uk.ac.manchester.cs.spinnaker.job;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

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
import javax.ws.rs.core.Response;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

@Path("/job")
public interface JobManagerInterface {
	String APPLICATION_ZIP = "application/zip";
	String JOB_PROCESS_MANAGER_ZIP = "RemoteSpiNNakerJobProcessManager.zip";

	/**
	 * Get the next job to be run on the given executer.
	 * 
	 * @param executerId
	 *            the ID of the executer that is asking.
	 */
	@GET
	@Path("next")
	@Produces(APPLICATION_JSON)
	Job getNextJob(@QueryParam("executerId") String executerId);

	@GET
	@Path("{id}/machine/max")
	@Produces(APPLICATION_JSON)
	SpinnakerMachine getLargestJobMachine(@PathParam("id") int id,
			@QueryParam("runTime") @DefaultValue("-1") double runTime);

	@GET
	@Path("{id}/machine")
	@Produces(APPLICATION_JSON)
	SpinnakerMachine getJobMachine(@PathParam("id") int id,
			@QueryParam("nCores") @DefaultValue("-1") int nCores,
			@QueryParam("nChips") @DefaultValue("-1") int nChips,
			@QueryParam("nBoards") @DefaultValue("-1") int nBoards,
			@QueryParam("runTime") @DefaultValue("-1") double runTime);

	@GET
	@Path("{id}/machine/checkLease")
	@Produces(APPLICATION_JSON)
	JobMachineAllocated checkMachineLease(@PathParam("id") int id,
			@QueryParam("waitTime") @DefaultValue("10000") int waitTime);

	@GET
	@Path("{id}/machine/extendLease")
	void extendJobMachineLease(@PathParam("id") int id,
			@QueryParam("runTime") @DefaultValue("-1") double runTime);

	@POST
	@Path("{id}/log")
	@Consumes("text/plain")
	void appendLog(@PathParam("id") int id, String logToAppend);

	@POST
	@Path("{id}/provenance")
	@Consumes(TEXT_PLAIN)
	void addProvenance(@PathParam("id") int id,
			@QueryParam("name") String name, @QueryParam("value") String value,
			String bodyValue);

	@POST
	@Path("{projectId}/{id}/addoutput")
	@Consumes(APPLICATION_OCTET_STREAM)
	void addOutput(@PathParam("projectId") String projectId,
			@PathParam("id") int id,
			@QueryParam("outputFilename") String output, InputStream input);

	@POST
	@Path("{projectId}/{id}/finished")
	@Consumes(TEXT_PLAIN)
	void setJobFinished(@PathParam("projectId") String projectId,
			@PathParam("id") int id, String logToAppend,
			@QueryParam("baseFilename") String baseFilename,
			@QueryParam("outputFilename") List<String> outputs);

	@POST
	@Path("{projectId}/{id}/error")
	@Consumes(APPLICATION_JSON)
	void setJobError(@PathParam("projectId") String projectId,
			@PathParam("id") int id, @QueryParam("error") String error,
			@QueryParam("logToAppend") String logToAppend,
			@QueryParam("baseFilename") String baseFilename,
			@QueryParam("outputFilename") List<String> outputs,
			RemoteStackTrace stackTrace);

	@GET
	@Path(JOB_PROCESS_MANAGER_ZIP)
	@Produces(APPLICATION_ZIP)
	Response getJobProcessManager();
}
