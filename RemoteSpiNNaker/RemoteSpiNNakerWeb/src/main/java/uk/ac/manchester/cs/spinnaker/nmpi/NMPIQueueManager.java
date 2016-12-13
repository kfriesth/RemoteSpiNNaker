package uk.ac.manchester.cs.spinnaker.nmpi;

import static org.joda.time.DateTimeZone.UTC;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.ac.manchester.cs.spinnaker.rest.utils.RestClientUtils.createApiKeyClient;
import static uk.ac.manchester.cs.spinnaker.rest.utils.RestClientUtils.createBasicClient;
import static uk.ac.manchester.cs.spinnaker.utils.ThreadUtils.sleep;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job.nmpi.QueueEmpty;
import uk.ac.manchester.cs.spinnaker.job.nmpi.QueueNextResponse;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage;
import uk.ac.manchester.cs.spinnaker.model.NMPILog;
import uk.ac.manchester.cs.spinnaker.rest.NMPIQueue;
import uk.ac.manchester.cs.spinnaker.rest.utils.CustomJacksonJsonProvider;
import uk.ac.manchester.cs.spinnaker.rest.utils.PropertyBasedDeserialiser;

/**
 * Manages the NMPI queue, receiving jobs and submitting them to be run
 */
public class NMPIQueueManager implements Runnable {
	/**
	 * The amount of time to sleep when an empty queue is detected.
	 */
	private static final int EMPTY_QUEUE_SLEEP_MS = 10000;

	/** The queue to get jobs from */
	private NMPIQueue queue;
	/** Marker to indicate if the manager is done or not */
	private boolean done = false;
	/** The set of listeners for this queue */
	private final Set<NMPIQueueListener> listeners = new HashSet<>();
	/** The log of the job so far */
	private final Map<Integer, NMPILog> jobLog = new HashMap<>();
	private Logger logger = getLogger(getClass());

	/** The hardware identifier for the queue */
	@Value("${nmpi.hardware}")
	private String hardware;
	/** The URL from which to load the data */
    @Value("${nmpi.url}")
    private URL nmpiUrl;
    /** The username to log in to the server with */
    @Value("${nmpi.username}")
    private String nmpiUsername;
    /** The password or API key to log in to the server with */
    @Value("${nmpi.password}")
    private String nmpiPassword;
	/**
	 * True if the password is an API key, False if the password should be used
	 * to obtain the key
	 */
    @Value("${nmpi.passwordIsApiKey}")
    private boolean nmpiPasswordIsApiKey;

    @Autowired
    private JobStorage storage;

    @PostConstruct
	private void initAPIClient() {
		CustomJacksonJsonProvider provider = new CustomJacksonJsonProvider();

		@SuppressWarnings("serial")
		class QueueResponseDeserialiser extends
				PropertyBasedDeserialiser<QueueNextResponse> {
			public QueueResponseDeserialiser() {
				super(QueueNextResponse.class);
				register("id", Job.class);
				register("warning", QueueEmpty.class);
			}
		}
		provider.addDeserialiser(QueueNextResponse.class,
				new QueueResponseDeserialiser());

		String apiKey = nmpiPassword;
		if (!nmpiPasswordIsApiKey) {
			queue = createBasicClient(nmpiUrl, nmpiUsername, nmpiPassword,
					NMPIQueue.class);
			apiKey = queue.getToken(nmpiUsername).getKey();
		}
		queue = createApiKeyClient(nmpiUrl, nmpiUsername, apiKey,
				NMPIQueue.class, provider);
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
				processResponse(queue.getNextJob(hardware));
			} catch (Exception e) {
				logger.error("Error in getting next job", e);
				sleep(EMPTY_QUEUE_SLEEP_MS);
			}
		}
	}

	private void processResponse(QueueNextResponse response)
			throws MalformedURLException {
		if (response instanceof QueueEmpty)
			sleep(EMPTY_QUEUE_SLEEP_MS);
		else if (response instanceof Job)
			processResponse((Job) response);
		else
			throw new IllegalStateException();
	}

	private void processResponse(Job job) throws MalformedURLException {
		storage.addJob(job);
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
			queue.updateJob(job.getId(), job);
		} catch (IOException e) {
			logger.error("Error in executing job", e);
			setJobError(job, null, null, e, 0, null);
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
		queue.updateLog(id, existingLog);
	}

	public void setJobRunning(Job job) {
		logger.debug("Job " + job.getId() + " is running");
		job.setStatus("running");
		logger.debug("Updating job status on server");
		queue.updateJob(job.getId(), job);
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
	public void setJobFinished(Job job, String logToAppend,
			List<DataItem> outputs, long resourceUsage,
			Map<String, String> provenance) throws MalformedURLException {
		logger.debug("Job " + job.getId() + " is finished");

		if (logToAppend != null)
			appendJobLog(job.getId(), logToAppend);

		job.setStatus("finished");
		job.setOutputData(outputs);
		job.setTimestampCompletion(new DateTime(UTC));
		job.setResourceUsage(resourceUsage);
		job.setProvenance(provenance);

		logger.debug("Updating job status on server");
		queue.updateJob(job.getId(), job);
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
	public void setJobError(Job job, String logToAppend, List<DataItem> outputs,
			Throwable error, long resourceUsage, Map<String, String> provenance)
			throws MalformedURLException {
		logger.debug("Job " + job.getId() + " finished with an error");
		StringWriter errors = new StringWriter();
		error.printStackTrace(new PrintWriter(errors));
		StringBuilder logMessage = new StringBuilder();
		if (logToAppend != null)
			logMessage.append(logToAppend);
		if (jobLog.containsKey(job.getId()) || logMessage.length() > 0)
			logMessage.append("\n\n==================\n");
		logMessage.append("Error:\n");
		logMessage.append(errors.toString());
		appendJobLog(job.getId(), logMessage.toString());

		job.setStatus("error");
		job.setTimestampCompletion(new DateTime(UTC));
		job.setOutputData(outputs);
		job.setResourceUsage(resourceUsage);
		job.setProvenance(provenance);

		logger.debug("Updating job on server");
		queue.updateJob(job.getId(), job);
	}

	/**
	 * Close the manager
	 */
	public void close() {
		done = true;
	}
}
