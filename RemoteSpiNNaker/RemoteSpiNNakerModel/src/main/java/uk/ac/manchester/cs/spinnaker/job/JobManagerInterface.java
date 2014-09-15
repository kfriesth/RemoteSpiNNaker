package uk.ac.manchester.cs.spinnaker.job;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

@Path("job")
public interface JobManagerInterface {

	@POST
	@Path("{id}/log")
	@Consumes("text/plain")
	public void appendLog(@PathParam("id") int id, String logToAppend);

	@POST
	@Path("{id}/finished")
	public void setJobFinished(@PathParam("id") int id, String logToAppend,
			@QueryParam("outputFilename") List<String> outputs);

	@POST
	@Path("{id}/error")
	public void setJobError(@PathParam("id") int id,
			@QueryParam("error") String error, RemoteStackTrace stackTrace);
}
