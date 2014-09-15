package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.module.SimpleModule;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTrace;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTraceElement;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.MachineManager;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueListener;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueManager;

/**
 * The manager of jobs; synchronizes and manages all the ongoing and future
 * processes and machines.
 */
public class JobManager implements NMPIQueueListener, JobManagerInterface {

	private static final String JOB_PROCESS_MANAGER_ZIP =
			"/RemoteSpiNNakerJobProcessManager.zip";

	private static final String JOB_PROCESS_MANAGER_MAIN_CLASS =
			"uk.ac.manchester.cs.spinnaker.jobprocessmanager.JobProcessManager";

	private static File getJavaExec() throws IOException {
		File binDir = new File(System.getProperty("java.home"), "bin");
		File exec = new File(binDir, "java");
		if (!exec.canExecute()) {
			exec = new File(binDir, "java.exe");
		}
		return exec;
	}

	private MachineManager machineManager = null;

	private NMPIQueueManager queueManager = null;

	private List<JobParametersFactory> jobParametersFactories = null;

	private List<File> jobProcessManagerClasspath = new ArrayList<File>();

	private URL baseUrl = null;

	public JobManager(MachineManager machineManager,
			NMPIQueueManager queueManager,
			List<JobParametersFactory> jobParametersFactories,
			URL baseUrl)
					throws IOException {
		this.machineManager = machineManager;
		this.queueManager = queueManager;
		this.jobParametersFactories = jobParametersFactories;
		this.baseUrl = baseUrl;

		queueManager.addListener(this);

		// Create a temporary folder for the job process manager
		File jobProcessManagerDirectory =
				File.createTempFile("processManager", "tmp");
		jobProcessManagerDirectory.delete();
		jobProcessManagerDirectory.mkdirs();
		jobProcessManagerDirectory.deleteOnExit();

		// Find the JobManager resource
		InputStream jobManagerStream = getClass().getResourceAsStream(
				JOB_PROCESS_MANAGER_ZIP);
		if (jobManagerStream == null) {
			throw new UnsatisfiedLinkError(JOB_PROCESS_MANAGER_ZIP
					+ " not found in classpath");
		}

		// Extract the JobManager resources
		ZipInputStream input = new ZipInputStream(jobManagerStream);
		ZipEntry entry = input.getNextEntry();
		while (entry != null) {
			File entryFile = new File(jobProcessManagerDirectory,
					entry.getName());
			if (entryFile.isDirectory()) {
				entryFile.mkdirs();
			} else {
			    FileOutputStream output = new FileOutputStream(entryFile);
			    byte[] buffer = new byte[8196];
			    int bytesRead = input.read(buffer);
			    while (bytesRead != -1) {
			    	output.write(buffer, 0, bytesRead);
			    	bytesRead = input.read(buffer);
			    }
			    output.close();
			    entryFile.deleteOnExit();

			    if (entryFile.getName().endsWith(".jar")) {
			    	jobProcessManagerClasspath.add(entryFile);
			    }
			}
			entry = input.getNextEntry();
		}
		input.close();
	}

	@Override
	public void addJob(int id, String experimentDescription,
			List<String> inputData, String hardwareConfig) throws IOException {

		// Get a machine to run the job on
		SpinnakerMachine machine = machineManager.getNextAvailableMachine();

		// Get job parameters if possible
		JobParameters parameters = null;
		Map<String, JobParametersFactoryException> errors =
				new HashMap<String, JobParametersFactoryException>();
		for (JobParametersFactory factory : jobParametersFactories) {
			try {
				parameters = factory.getJobParameters(experimentDescription,
						inputData, hardwareConfig);
			} catch (UnsupportedJobException e) {

				// Do Nothing
			} catch (JobParametersFactoryException e) {
				errors.put(factory.getClass().getSimpleName(), e);
			}
		}
		if (parameters == null) {
			if (!errors.isEmpty()) {
				StringBuilder problemBuilder = new StringBuilder();
				problemBuilder.append("The job type was recognised by at least"
						+ " one factory, but could not be decoded.  The errors "
						+ " are as follows:\n");
				for (String key : errors.keySet()) {
					JobParametersFactoryException error = errors.get(key);
					problemBuilder.append(key);
					problemBuilder.append(": ");
					problemBuilder.append(error.getMessage());
					problemBuilder.append('\n');
				}
				throw new IOException(problemBuilder.toString());
			}
			throw new IOException(
					"The job did not appear to be supported on this system");
		}

		JobExecuter executer = new JobExecuter(getJavaExec(),
				jobProcessManagerClasspath,
				JOB_PROCESS_MANAGER_MAIN_CLASS,
				new ArrayList<String>(),
				File.createTempFile("job", ".tmp"));
		OutputStream output = executer.getProcessOutputStream();

		// Send the job executed the details of what to run
		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy(PropertyNamingStrategy
		        .CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
		JobParametersSerializer serializer =
				new JobParametersSerializer("jobType");
		SimpleModule serializerModule = new SimpleModule();
		serializerModule.addSerializer(JobParameters.class, serializer);
		mapper.registerModule(serializerModule);

		mapper.writeValue(output, machine);
		mapper.writeValue(output, parameters);

		PrintWriter writer = new PrintWriter(output);
		writer.println(id);
		writer.println(baseUrl.toString());
		writer.flush();
		output.flush();
	}

	@Override
	public void appendLog(int id, String logToAppend) {
		queueManager.appendJobLog(id, logToAppend);
	}

	@Override
	public void setJobFinished(int id, String logToAppend,
			List<String> outputs) {
		List<File> outputFiles = new ArrayList<File>();
		for (String filename : outputs) {
			outputFiles.add(new File(filename));
		}
		try {
			queueManager.setJobFinished(id, logToAppend, outputFiles);
		} catch (MalformedURLException e) {
			System.err.println("Error creating URLs while updating job");
			e.printStackTrace();
		}
	}

	@Override
	public void setJobError(int id, String error, RemoteStackTrace stackTrace) {
		StackTraceElement[] elements =
				new StackTraceElement[stackTrace.getElements().size()];
		int i = 0;
		for (RemoteStackTraceElement element : stackTrace.getElements()) {
			elements[i] = new StackTraceElement(element.getClassName(),
					element.getMethodName(), element.getFileName(),
					element.getLineNumber());
		}

		Exception exception = new Exception(error);
		exception.setStackTrace(elements);
		queueManager.setJobError(id, null, exception);
	}
}
