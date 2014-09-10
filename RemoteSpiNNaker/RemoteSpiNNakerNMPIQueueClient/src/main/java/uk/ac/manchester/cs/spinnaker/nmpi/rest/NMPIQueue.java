package uk.ac.manchester.cs.spinnaker.nmpi.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import uk.ac.manchester.cs.spinnaker.nmpi.model.Job;
import uk.ac.manchester.cs.spinnaker.nmpi.model.QueueNextResponse;

@Path("/api/v1/queue")
public interface NMPIQueue {

	@GET
	@Path("submitted/next/{hardware}")
	@Produces("application/json")
	QueueNextResponse getNextJob(@PathParam("hardware") String hardware);

    @PUT
    @Path("{id}")
    @Consumes("application/json")
    void updateJob(@PathParam("id") int id, Job job);

    @GET
    @Path("{id}")
    @Produces("application/json")
    Job getJob(@PathParam("id") int id);
}
