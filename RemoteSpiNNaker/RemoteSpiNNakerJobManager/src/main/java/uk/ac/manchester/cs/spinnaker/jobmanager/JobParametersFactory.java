package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.File;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;

/**
 * A factory that produces job parameters
 */
public interface JobParametersFactory {

    /**
    * The argument to append to the script name to request that the system is
    * added to the command line
    */
    final String SYSTEM_ARG = " {system}";

    /**
     * Gets job parameters given job description data
     * @param experimentDescription A description of the experiment to be run
     * @param command The command to execute the job
     * @param inputData A list of input data to be processed
     * @param hardwareConfiguration The configuration of the hardware
     * @param workingDirectory The working directory where the job will be run
     * @param deleteJobOnExit True if the job should be deleted when complete
     * @return A job description to be executed
     * @throws UnsupportedJobException If the factory does not support the job
     * @throws JobParametersFactoryException If there was an error getting
     *                                       the parameters
     */
    JobParameters getJobParameters(String experimentDescription, String command,
            List<String> inputData, Map<String, Object> hardwareConfiguration,
            File workingDirectory, boolean deleteJobOnExit)
            throws UnsupportedJobException, JobParametersFactoryException;
}
