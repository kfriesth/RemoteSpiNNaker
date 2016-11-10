package uk.ac.manchester.cs.spinnaker.nmpi;

import static org.joda.time.DateTimeZone.UTC;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.ac.manchester.cs.spinnaker.rest.RestClientUtils.createApiKeyClient;
import static uk.ac.manchester.cs.spinnaker.rest.RestClientUtils.createBasicClient;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;

import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job.nmpi.QueueEmpty;
import uk.ac.manchester.cs.spinnaker.job.nmpi.QueueNextResponse;
import uk.ac.manchester.cs.spinnaker.nmpi.model.NMPILog;
import uk.ac.manchester.cs.spinnaker.nmpi.rest.NMPIQueue;
import uk.ac.manchester.cs.spinnaker.nmpi.rest.QueueResponseDeserialiser;
import uk.ac.manchester.cs.spinnaker.rest.CustomJacksonJsonProvider;

/**
 * Manages the NMPI queue, receiving jobs and submitting them to be run
 */
public class NMPIQueueManager implements Runnable {
	/**
	 * The amount of time to sleep when an empty queue is detected.
	 */
	private static final int EMPTY_QUEUE_SLEEP_MS = 10000;

	/**
	 * The queue to get jobs from
	 */
	private NMPIQueue queue;

	/**
	 * Marker to indicate if the manager is done or not
	 */
	private boolean done = false;

	/**
	 * The hardware identifier for the queue
	 */
	private final String hardware;

	/**
	 * The set of listeners for this queue
	 */
	private final Set<NMPIQueueListener> listeners = new HashSet<>();

	/**
	 * A cache of jobs that have been received
	 */
	private final Map<Integer, Job> jobCache = new HashMap<>();

	/**
	 * The log of the job so far
	 */
	private final Map<Integer, NMPILog> jobLog = new HashMap<>();

	/**
	 * The logger
	 */
	private Logger logger = getLogger(getClass());

	/**
	 * Creates a new Manager, pointing at a queue at a specific URL.
	 * 
	 * @param url
	 *            The URL from which to load the data
	 * @param hardware
	 *            The name of the hardware that this queue is for
	 * @param username
	 *            The username to log in to the server with
	 * @param password
	 *            The password or API key to log in to the server with
	 * @param passwordIsKey
	 *            True if the password is an API key, False if the password
	 *            should be used to obtain the key
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 */
	public NMPIQueueManager(URL url, String hardware, String username,
			String password, boolean passwordIsKey) {
		CustomJacksonJsonProvider provider = new CustomJacksonJsonProvider();
		provider.addDeserialiser(QueueNextResponse.class,
				new QueueResponseDeserialiser());

		String apiKey = password;
		if (!passwordIsKey) {
			queue = createBasicClient(url, username, password, NMPIQueue.class);
			apiKey = queue.getToken(username).getKey();
		}
		queue = createApiKeyClient(url, username, apiKey, NMPIQueue.class,
				provider);

		this.hardware = hardware;
	}

	/**
	 * Gets a job from the cache, or from the server if the job is not in the
	 * cache
	 * 
	 * @param id
	 *            The id of the job
	 * @return The job
	 */
	private Job getJob(int id) {
		synchronized (jobCache) {
			Job job = jobCache.get(id);
			if (job == null) {
				synchronized (queue) {
					job = queue.getJob(id);
				}
				jobCache.put(id, job);
			}
			return job;
		}
	}

	/**
	 * Register a listener against the manager for new jobs
	 * 
	 * @param listener
	 *            The listener to register
	 */
	public void addListener(NMPIQueueListener listener) {
		listeners.add(listener);
	}

	@Override
	public void run() {
		while (!done) {
			try {
				// logger.debug("Getting next job");
				QueueNextResponse response;
				synchronized (queue) {
					response = queue.getNextJob(hardware);
				}
				processResponse(response);
			} catch (Exception e) {
				logger.error("Error in getting next job", e);
				snooze();
			}
		}
	}

	private void snooze() {
		try {
			Thread.sleep(EMPTY_QUEUE_SLEEP_MS);
		} catch (InterruptedException e1) {
			// Do Nothing
		}
	}

	private void processResponse(QueueNextResponse response)
			throws MalformedURLException {
		if (response instanceof QueueEmpty) {
			snooze();
		} else if (response instanceof Job) {
			Job job = (Job) response;
			synchronized (jobCache) {
				jobCache.put(job.getId(), job);
			}
			logger.debug("Job " + job.getId() + " received");
			try {
				for (NMPIQueueListener listener : listeners)
					listener.addJob(job);
				logger.debug("Setting job status to queued");
				job.setTimestampSubmission(job.getTimestampSubmission()
						.withZoneRetainFields(UTC));
				job.setTimestampCompletion(null);
				job.setStatus("queued");
				logger.debug("Updating job status on server");
				synchronized (queue) {
					queue.updateJob(job.getId(), job);
				}
			} catch (IOException e) {
				logger.error("Error in executing job", e);
				setJobError(job.getId(), null, null, e, 0, null);
			}
		}
	}

	/**
	 * Appends log messages to the log
	 * 
	 * @param id
	 *            The id of the job
	 * @param logToAppend
	 *            The messages to append
	 */
	public void appendJobLog(int id, String logToAppend) {
		NMPILog existingLog = jobLog.get(id);
		if (existingLog == null) {
			existingLog = new NMPILog();
			jobLog.put(id, existingLog);
		}
		existingLog.appendContent(logToAppend);
		logger.debug("Job " + id + " log is being updated");
		synchronized (queue) {
			queue.updateLog(id, existingLog);
		}
	}

	public void setJobRunning(int id) {
		logger.debug("Job " + id + " is running");
		Job job = getJob(id);
		job.setStatus("running");
		logger.debug("Updating job status on server");
		synchronized (queue) {
			queue.updateJob(id, job);
		}
	}

	/**
	 * Marks a job as finished successfully
	 * 
	 * @param id
	 *            The id of the job
	 * @param logToAppend
	 *            Any additional log messages to append to the existing log
	 *            (null if none)
	 * @param outputs
	 *            The outputs of the job (null if none)
	 * @throws MalformedURLException
	 */
	public void setJobFinished(int id, String logToAppend,
			List<DataItem> outputs, long resourceUsage,
			Map<String, String> provenance) throws MalformedURLException {
		logger.debug("Job " + id + " is finished");

		if (logToAppend != null)
			appendJobLog(id, logToAppend);

		Job job = getJob(id);
		job.setStatus("finished");
		job.setOutputData(outputs);
		job.setTimestampCompletion(new DateTime(DateTimeZone.UTC));
		job.setResourceUsage(resourceUsage);

		// TODO: Make provenance work
		// job.setProvenance(provenance);

		logger.debug("Updating job status on server");
		synchronized (queue) {
			queue.updateJob(id, job);
		}
	}

	/**
	 * Marks a job as finished with an error
	 * 
	 * @param id
	 *            The id of the job
	 * @param logToAppend
	 *            Any additional log messages to append to the existing log
	 *            (null if none)
	 * @param outputs
	 *            Any outputs generated, or null if none
	 * @param error
	 *            The error details
	 * @throws MalformedURLException
	 */
	public void setJobError(int id, String logToAppend, List<DataItem> outputs,
			Throwable error, long resourceUsage, Map<String, String> provenance)
			throws MalformedURLException {
		logger.debug("Job " + id + " finished with an error");
		StringWriter errors = new StringWriter();
		error.printStackTrace(new PrintWriter(errors));
		StringBuilder logMessage = new StringBuilder();
		if (logToAppend != null)
			logMessage.append(logToAppend);
		if (jobLog.containsKey(id) || logMessage.length() > 0)
			logMessage.append("\n\n==================\n");
		logMessage.append("Error:\n");
		logMessage.append(errors.toString());
		appendJobLog(id, logMessage.toString());

		Job job = getJob(id);
		job.setStatus("error");
		job.setTimestampCompletion(new DateTime(DateTimeZone.UTC));
		job.setOutputData(outputs);
		job.setResourceUsage(resourceUsage);

		// TODO: Make provenance work
		// job.setProvenance(provenance);

		logger.debug("Updating job on server");
		synchronized (queue) {
			queue.updateJob(id, job);
		}
	}

	/**
	 * Close the manager
	 */
	public void close() {
		done = true;
	}
}
