package uk.ac.manchester.cs.spinnaker.jobprocessmanager;

import static java.io.File.createTempFile;
import static uk.ac.manchester.cs.spinnaker.jobprocessmanager.FileDownloader.downloadFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTrace;
import uk.ac.manchester.cs.spinnaker.job.Status;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.job_parameters.UnsupportedJobException;
import uk.ac.manchester.cs.spinnaker.job_parameters.impl.DirectPyNNJobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.impl.GitPyNNJobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.impl.ZipPyNNJobParametersFactory;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcess;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcessFactory;
import uk.ac.manchester.cs.spinnaker.jobprocess.impl.JobManagerLogWriter;
import uk.ac.manchester.cs.spinnaker.jobprocess.impl.PyNNJobProcess;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

/**
 * Manages a running job process.  This is run as a separate process from the
 * command line, and it assumes input is passed via {@link System#in}.
 */
public class JobProcessManager {
	// No instances for you!
	private JobProcessManager(){}

	/** The factories for converting jobs into parameters. */
	private static final JobParametersFactory[] JOB_PARAMETER_FACTORIES = new JobParametersFactory[] {
			new GitPyNNJobParametersFactory(),
			new ZipPyNNJobParametersFactory(),
			new DirectPyNNJobParametersFactory() };

	/** The factory for converting parameters into processes. */
	private static final JobProcessFactory JOB_PROCESS_FACTORY = new JobProcessFactory() {
		{
			addMapping(PyNNJobParameters.class, PyNNJobProcess.class);
		}
	};

	/** How to talk to the main website. */
    private static JobManagerInterface createJobManager(String url) {
        ResteasyClient client = new ResteasyClientBuilder().build();
        client.register(new JacksonJsonProvider());
        // TODO Add auth filter
        ResteasyWebTarget target = client.target(url);
        return target.proxy(JobManagerInterface.class);
    }

    private static void deleteDirectory(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory())
                deleteDirectory(file);
            else
                file.delete();
        }
        directory.delete();
    }

    private static String serverUrl = null;
    private static boolean deleteOnExit = false;
    private static boolean isLocal = false;
    private static String executerId = null;
    private static boolean liveUploadOutput = false;
    private static boolean requestMachine = false;

    public static void main(String[] args) throws Exception {
        parseArguments(args);

        JobManagerInterface jobManager = null;
        JobManagerLogWriter logWriter = null;
        Job job = null;
        String projectId = null;
        
        try {
            jobManager = createJobManager(serverUrl);

            // Read the job
            job = jobManager.getNextJob(executerId);
            projectId = new File(job.getCollabId()).getName();

            // Create a temporary location for the job
            File workingDirectory = createTempFile("job", ".tmp");
            workingDirectory.delete();
            workingDirectory.mkdirs();

            JobParameters parameters = getJobParameters(job, workingDirectory);

            // Create a process to process the request
            System.err.println("Creating process from parameters");
            JobProcess<JobParameters> process =
                    JOB_PROCESS_FACTORY.createProcess(parameters);

            logWriter = new JobManagerLogWriter(
                createJobManager(serverUrl), job.getId(), liveUploadOutput);

            // Read the machine
            // (get a 3 board machine just now)
            SpinnakerMachine machine = null;
            String machineUrl = null;
            if (requestMachine) {
                machine = jobManager.getJobMachine(job.getId(), -1, -1, -1, -1);
            } else {
                machineUrl = serverUrl + "job/" + job.getId() + "/machine";
            }

            // Execute the process
            System.err.println("Running job " + job.getId() + " on "
                    + machine + " using "
                    + parameters.getClass()
                    + " reporting to " + serverUrl);
            process.execute(machineUrl, machine, parameters, logWriter);
            logWriter.stop();
            String log = logWriter.getLog();

            // Get the exit status
            Status status = process.getStatus();
            System.err.println("Process has finished with status " + status);
            List<File> outputs = process.getOutputs();
            List<String> outputsAsStrings = new ArrayList<>();
            if (isLocal) {
                for (File output : outputs)
                    outputsAsStrings.add(output.getAbsolutePath());
            } else {
                for (File output : outputs) {
                    InputStream input = new FileInputStream(output);
                    jobManager.addOutput(
                        projectId, job.getId(), output.getName(), input);
                    input.close();
                }
            }
			switch (status) {
			case Error:
				Throwable error = process.getError();
				jobManager.setJobError(projectId, job.getId(),
						error.getMessage(), log,
						workingDirectory.getAbsolutePath(), outputsAsStrings,
						new RemoteStackTrace(error));
				break;
			case Finished:
				jobManager.setJobFinished(projectId, job.getId(), log,
						workingDirectory.getAbsolutePath(), outputsAsStrings);

				// Clean up
				process.cleanup();
				if (deleteOnExit)
					deleteDirectory(workingDirectory);
				break;
			default:
				throw new RuntimeException("Unknown status returned!");
			}
        } catch (Throwable error) {
            if (jobManager != null && job != null) {
                try {
                    String log = "";
                    if (logWriter != null) {
                        logWriter.stop();
                        log = logWriter.getLog();
                    }
					jobManager.setJobError(projectId, job.getId(),
							error.getMessage(), log, "",
							new ArrayList<String>(),
							new RemoteStackTrace(error));
                } catch (Throwable t) {
                    t.printStackTrace();
                    error.printStackTrace();
                }
            } else {
                error.printStackTrace();
            }
        }
    }

	private static void parseArguments(String[] args) throws IOException {
		for (int i = 0; i < args.length; i++) {
        	if (args[i].equals("--serverUrl")) {
        		serverUrl = args[++i];
        	} else if (args[i].equals("--deleteOnExit")) {
        		deleteOnExit = true;
        	} else if (args[i].equals("--local")) {
        		isLocal = true;
        	} else if (args[i].equals("--executerId")) {
        		executerId = args[++i];
        	} else if (args[i].equals("--liveUploadOutput")) {
        		liveUploadOutput = true;
        	} else if (args[i].equals("--requestMachine")) {
        		requestMachine = true;
        	}
        }
        
        if (serverUrl == null) {
        	throw new IOException("--serverUrl must be specified");
        } else if (executerId == null) {
        	throw new IOException("--executerId must be specified");
        }
	}

	/**
	 * Sort out the parameters to a job. Includes downloading any necessary files.
	 * 
	 * @param job
	 *            The job that we're assembling the parameters for.
	 * @param workingDirectory
	 *            The working directory for the job, used to write files.
	 * @return Description of the parameters.
	 * @throws IOException
	 *             If anything goes wrong, such as the parameters being
	 *             unreadable or the job being unsupported on the current
	 *             architectural configuration.
	 */
	private static JobParameters getJobParameters(Job job, File workingDirectory)
			throws IOException {
		JobParameters parameters = null;
		Map<String, JobParametersFactoryException> errors = new HashMap<>();

		for (JobParametersFactory factory : JOB_PARAMETER_FACTORIES) {
			try {
				parameters = factory.getJobParameters(job, workingDirectory);
				break;
			} catch (UnsupportedJobException e) {
				// Do Nothing
			} catch (JobParametersFactoryException e) {
				errors.put(factory.getClass().getSimpleName(), e);
			}
		}

		if (parameters != null) {
            // Get any requested input files
			if (job.getInputData() != null)
				for (DataItem input : job.getInputData())
					downloadFile(new URL(input.getUrl()), workingDirectory,
							null);

			return parameters;
		}

		if (!errors.isEmpty()) {
			StringBuilder problemBuilder = new StringBuilder();
			problemBuilder.append("The job type was recognised by at least"
					+ " one factory, but could not be decoded.  The errors "
					+ " are as follows:\n");
			for (String key : errors.keySet())
				problemBuilder.append(key).append(": ")
						.append(errors.get(key).getMessage()).append('\n');
			throw new IOException(problemBuilder.toString());
		}

		// Miscellaneous other error
		throw new IOException(
				"The job did not appear to be supported on this system");
	}
}
