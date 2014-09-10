package uk.ac.manchester.cs.spinnaker.jobprocessmanager;

import java.io.File;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.module.SimpleModule;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.Status;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobParametersDeserializer;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcess;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcessFactory;
import uk.ac.manchester.cs.spinnaker.jobprocess.impl.PyNNJobProcess;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

/**
 * Manages a running job process.  This is run as a separate process from the
 * command line, and it assumes input is passed via System.in.
 *
 */
public class JobProcessManager {

	// Prepare a factory for converting parameters into processes
	private static final JobProcessFactory FACTORY = new JobProcessFactory() {{
		addMapping(PyNNJobParameters.class, PyNNJobProcess.class);
	}};

	public static void main(String[] args) throws Exception {

		try {

			// Prepare to decode JSON parameters
			ObjectMapper mapper = new ObjectMapper();
			mapper.setPropertyNamingStrategy(PropertyNamingStrategy
			        .CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
			JobParametersDeserializer deserializer =
					new JobParametersDeserializer("jobType",
							FACTORY.getParameterTypes());
			SimpleModule deserializerModule = new SimpleModule();
			deserializerModule.addDeserializer(JobParameters.class,
					deserializer);
			mapper.registerModule(deserializerModule);

			// Read the machine from System.in
			SpinnakerMachine machine = mapper.readValue(System.in,
					SpinnakerMachine.class);

			// Read the job parameters from System.in
			JobParameters parameters = mapper.readValue(System.in,
					JobParameters.class);

			// Create a process to process the request
			JobProcess<JobParameters> process =
					FACTORY.createProcess(parameters);

			// Execute the process
			process.execute(machine, parameters);

			// Get the exit status
			Status status = process.getStatus();
			if (status == Status.Error) {
				Throwable error = process.getError();
				// TODO: Send the error back
			} else if (status == Status.Finished) {
				List<File> outputs = process.getOutputs();
				// TODO: Send the results back
			} else {
				throw new RuntimeException("Unknown status returned!");
			}

		} catch (Throwable error) {
			// TODO: Send the error back
		}
	}
}
