package uk.ac.manchester.cs.spinnaker.rest;

import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;

/**
 * An interface to the UNICORE storage REST API.
 */
@Path("/storages")
public interface UnicoreFileClient {
	/**
	 * Upload a file
	 * 
	 * @param id
	 *            The id of the storage on the server.
	 * @param filePath
	 *            The path at which to store the file (directories are
	 *            automatically created).
	 * @param input
	 *            The input stream containing the file to upload.
	 * @throws WebApplicationException
	 *             If anything goes wrong.
	 */
	@PUT
	@Path("{id}/files/{filePath}")
	@Consumes("application/octet-stream")
	public void upload(@PathParam("id") String id,
			@PathParam("filePath") String filePath, InputStream input)
			throws WebApplicationException;
}
