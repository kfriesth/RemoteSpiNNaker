package uk.ac.manchester.cs.spinnaker.nmpi;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import uk.ac.manchester.cs.spinnaker.nmpi.model.Job;
import uk.ac.manchester.cs.spinnaker.nmpi.model.QueueEmpty;
import uk.ac.manchester.cs.spinnaker.nmpi.model.QueueNextResponse;
import uk.ac.manchester.cs.spinnaker.nmpi.rest.NMPIJacksonJsonProvider;
import uk.ac.manchester.cs.spinnaker.nmpi.rest.NMPIQueue;
import uk.ac.manchester.cs.spinnaker.nmpi.rest.PropertyBasedDeserialiser;

/**
 * Manages the NMPI queue, receiving jobs and submitting them to be run
 */
@Path("output")
public class NMPIQueueManager extends Thread {

	/**
	 * The amount of time to sleep when an empty queue is detected
	 */
	private static final int EMPTY_QUEUE_SLEEP_MS = 10000;

	/**
	 * The queue to get jobs from
	 */
	private NMPIQueue queue = null;

	/**
	 * Marker to indicate if the manager is done or not
	 */
	private boolean done = false;

	/**
	 * The hardware identifier for the queue
	 */
	private String hardware = null;

	/**
	 * The place where results should be stored
	 */
	private File resultsDirectory = null;

	/**
	 * The set of listeners for this queue
	 */
	private Set<NMPIQueueListener> listeners = new HashSet<NMPIQueueListener>();

	/**
	 * A cache of jobs that have been received
	 */
	private Map<Integer, Job> jobCache = new HashMap<Integer, Job>();

	/**
	 * The URL of the server up to this application
	 */
	private URL baseServerUrl = null;

	/**
	 * Creates a new Manager, pointing at a queue at a specific URL
	 * @param url The URL from which to load the data
	 * @param hardware The name of the hardware that this queue is for
	 * @param resultsDirectory The directory where results are stored
	 * @param baseServerUrl The URL of the server up to this rest resource
	 */
	public NMPIQueueManager(String url, String hardware,
			File resultsDirectory, URL baseServerUrl) {
		PropertyBasedDeserialiser<QueueNextResponse> deserialiser =
				new PropertyBasedDeserialiser<QueueNextResponse>(
						QueueNextResponse.class);
		deserialiser.register("id", Job.class);
		deserialiser.register("warning", QueueEmpty.class);
		NMPIJacksonJsonProvider provider = new NMPIJacksonJsonProvider();
		provider.addDeserialiser(QueueNextResponse.class, deserialiser);

		ResteasyClient client = new ResteasyClientBuilder().build();
		client.register(provider);
        ResteasyWebTarget target = client.target(url);
        queue = target.proxy(NMPIQueue.class);

        this.hardware = hardware;
        this.resultsDirectory = resultsDirectory;
        this.baseServerUrl = baseServerUrl;
	}

	/**
	 * Gets a results file
	 * @param id The id of the job which produced the file
	 * @param filename The name of the file
	 * @return A response containing the file, or a "NOT FOUND" response if the
	 *         file does not exist
	 */
	@GET
	@Path("{id}/{filename}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response getResultFile(@PathParam("id") int id,
			@PathParam("filename") String filename) {
		File idDirectory = new File(resultsDirectory, String.valueOf(id));
		File resultFile = new File(idDirectory, filename);

		if (!resultFile.canRead()) {
			return Response.status(Status.NOT_FOUND).build();
		}

		return Response.ok((Object) resultFile)
				.header("Content-Disposition",
						"attachment; filename=" + filename)
				.build();
	}

	/**
	 * Gets a job from the cache, or from the server if the job is not in the
	 * cache
	 * @param id The id of the job
	 * @return The job
	 */
	private Job getJob(int id) {
		Job job = jobCache.get(id);
		if (job == null) {
			job = queue.getJob(id);
			jobCache.put(id, job);
		}
		return job;
	}

	/**
	 * Register a listener against the manager for new jobs
	 * @param listener The listener to register
	 */
	public void addListener(NMPIQueueListener listener) {
		listeners.add(listener);
	}

	@Override
	public void run() {
	    while (!done) {
		    QueueNextResponse response = queue.getNextJob(hardware);
		    if (response instanceof QueueEmpty) {
		    	try {
					Thread.sleep(EMPTY_QUEUE_SLEEP_MS);
				} catch (InterruptedException e) {

					// Do Nothing
				}
		    } else if (response instanceof Job) {
		    	Job job = (Job) response;
		    	try {
		    	    for (NMPIQueueListener listener: listeners) {
						listener.addJob(job.getId(),
								job.getExperimentDescription(),
								job.getInputData(),
								job.getHardwareConfig());
		    	    }
			    	job.setStatus("running");
				} catch (IOException e) {
					setJobError(job.getId(), null, e);
				}
		    	queue.updateJob(job.getId(), job);
		    }
	    }
	}

	/**
	 * Appends log messages to the log
	 * @param id The id of the job
	 * @param logToAppend The messages to append
	 */
	public void appendJobLog(int id, String logToAppend) {
		Job job = getJob(id);
		String existingLog = job.getLog();
		if (existingLog == null) {
			existingLog = logToAppend;
		} else {
			existingLog += logToAppend;
		}
		job.setLog(existingLog);
		queue.updateJob(id, job);
	}

	/**
	 * Marks a job as finished successfully
	 * @param id The id of the job
	 * @param logToAppend Any additional log messages to append to the existing
	 *        log (null if none)
	 * @param outputs The file outputs of the job (null if none)
	 * @throws MalformedURLException
	 */
	public void setJobFinished(int id, String logToAppend, List<File> outputs)
			throws MalformedURLException {
		File idDirectory = new File(resultsDirectory, String.valueOf(id));
		idDirectory.mkdirs();
		List<String> outputUrls = new ArrayList<String>();
		if (outputs != null) {
			for (File output : outputs) {
				File newOutput = new File(idDirectory, output.getName());
				output.renameTo(newOutput);
				URL outputUrl = new URL(baseServerUrl,
						id + "/" + output.getName());
				outputUrls.add(outputUrl.toExternalForm());
			}
		}

		Job job = getJob(id);
		job.setStatus("finished");
		job.setOutputData(outputUrls);
		if (logToAppend != null) {
			String existingLog = job.getLog();
			if (existingLog == null) {
				existingLog = logToAppend;
			} else {
				existingLog += logToAppend;
			}
			job.setLog(existingLog);
		}
		job.setTimestampCompletion(new Date());
		queue.updateJob(id, job);
	}

	/**
	 * Marks a job as finished with an error
	 * @param id The id of the job
	 * @param logToAppend Any additional log messages to append to the existing
	 *        log (null if none)
	 * @param error The error details
	 */
	public void setJobError(int id, String logToAppend, Throwable error) {
		StringWriter errors = new StringWriter();
		error.printStackTrace(new PrintWriter(errors));
		String logMessage = "Error:\n";
		logMessage += errors.toString();

		Job job = getJob(id);
		job.setStatus("error");
		String existingLog = job.getLog();
		if (logToAppend != null) {
			if (existingLog == null) {
				existingLog = logToAppend;
			} else {
				existingLog += logToAppend;
			}
		}
		if (existingLog == null || existingLog.isEmpty()) {
			existingLog = logMessage;
		} else {
			existingLog += "\n\n==================\n";
			existingLog += logMessage;
		}
		job.setLog(existingLog);
		job.setTimestampCompletion(new Date());
		queue.updateJob(id, job);
	}

	/**
	 * Close the manager
	 */
	public void close() {
		done = true;
	}
}
