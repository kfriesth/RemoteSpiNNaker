package uk.ac.manchester.cs.spinnaker.job_parameters;

import java.io.File;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;

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
     * @param job The job to be executed
     * @param workingDirectory The working directory where the job will be run
     * @return A job description to be executed
     * @throws UnsupportedJobException If the factory does not support the job
     * @throws JobParametersFactoryException If there was an error getting
     *                                       the parameters
     */
    JobParameters getJobParameters(Job job, File workingDirectory)
        throws UnsupportedJobException, JobParametersFactoryException;
}
