package uk.ac.manchester.cs.spinnaker.jobprocessmanager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;
import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.JobSpecification;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTrace;
import uk.ac.manchester.cs.spinnaker.job.Status;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobParametersDeserializer;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcess;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcessFactory;
import uk.ac.manchester.cs.spinnaker.jobprocess.impl.JobManagerLogWriter;
import uk.ac.manchester.cs.spinnaker.jobprocess.impl.PyNNJobProcess;

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

    private static JobManagerInterface createJobManager(String url) {
        ResteasyClient client = new ResteasyClientBuilder().build();
        ResteasyWebTarget target = client.target(url);
        return target.proxy(JobManagerInterface.class);
    }

    public static void main(String[] args) throws Exception {

        int id = -1;
        JobManagerInterface jobManager = null;
        JobManagerLogWriter logWriter = null;
        try {

            UnclosableInputStream in = new UnclosableInputStream(System.in);

            // Prepare to decode JSON parameters
            ObjectMapper mapper = new ObjectMapper();
            JobParametersDeserializer deserializer =
                    new JobParametersDeserializer("jobType",
                            FACTORY.getParameterTypes());
            SimpleModule deserializerModule = new SimpleModule();
            deserializerModule.addDeserializer(JobParameters.class,
                    deserializer);
            mapper.registerModule(deserializerModule);

            // Read the machine from System.in
            System.err.println("Reading specification");
            JobSpecification specification = mapper.readValue(in,
                    JobSpecification.class);
            System.err.println("Running job " + specification.getId() + " on "
                    + specification.getMachine().getMachineName() + " using "
                    + specification.getParameters().getClass()
                    + " reporting to " + specification.getUrl());

            jobManager = createJobManager(specification.getUrl());
            id = specification.getId();

            // Create a process to process the request
            System.err.println("Creating process from parameters");
            JobProcess<JobParameters> process =
                    FACTORY.createProcess(specification.getParameters());

            // Execute the process
            logWriter = new JobManagerLogWriter(
                    createJobManager(specification.getUrl()), id);
            System.err.println("Executing process");
            process.execute(specification.getMachine(),
                    specification.getParameters(), logWriter);
            String log = logWriter.getLog();

            // Get the exit status
            Status status = process.getStatus();
            System.err.println("Process has finished with status " + status);
            List<File> outputs = process.getOutputs();
            List<String> outputsAsStrings = new ArrayList<String>();
            for (File output : outputs) {
                outputsAsStrings.add(output.getAbsolutePath());
            }
            if (status == Status.Error) {
                Throwable error = process.getError();
                jobManager.setJobError(id, error.getMessage(), log,
                        outputsAsStrings, new RemoteStackTrace(error));
            } else if (status == Status.Finished) {
                jobManager.setJobFinished(id, log, outputsAsStrings);

                // Clean up
                process.cleanup();
            } else {
                throw new RuntimeException("Unknown status returned!");
            }

        } catch (Throwable error) {
            if (jobManager != null) {
                try {
                    String log = "";
                    if (logWriter != null) {
                        log = logWriter.getLog();
                    }
                    jobManager.setJobError(id, error.getMessage(), log,
                            new ArrayList<String>(),
                            new RemoteStackTrace(error));
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            } else {
                error.printStackTrace();
            }
        }
    }
}
