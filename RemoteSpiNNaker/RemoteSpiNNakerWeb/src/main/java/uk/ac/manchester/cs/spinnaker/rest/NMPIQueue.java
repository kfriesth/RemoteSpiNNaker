package uk.ac.manchester.cs.spinnaker.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job.nmpi.QueueNextResponse;
import uk.ac.manchester.cs.spinnaker.model.APIKeyResponse;
import uk.ac.manchester.cs.spinnaker.model.NMPILog;

@Path("/api/v2")
public interface NMPIQueue {
    @GET
    @Path("token/auth")
    @Produces("application/json")
    APIKeyResponse getToken(@QueryParam("username") String username);

    @GET
    @Path("queue/submitted/next/{hardware}/")
    @Produces("application/json")
    QueueNextResponse getNextJob(@PathParam("hardware") String hardware);

    @PUT
    @Path("queue/{id}")
    @Consumes("application/json")
    void updateJob(@PathParam("id") int id, Job job);

    @GET
    @Path("queue/{id}")
    @Produces("application/json")
    Job getJob(@PathParam("id") int id);

    @PUT
    @Path("log/{id}")
    @Consumes("application/json")
    void updateLog(@PathParam("id") int id, NMPILog log);
}
