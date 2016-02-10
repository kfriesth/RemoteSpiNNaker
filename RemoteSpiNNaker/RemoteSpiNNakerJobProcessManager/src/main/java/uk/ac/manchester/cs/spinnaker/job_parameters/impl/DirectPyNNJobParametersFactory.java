package uk.ac.manchester.cs.spinnaker.job_parameters.impl;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.job_parameters.UnsupportedJobException;

/**
 * A JobParametersFactory that uses the experimentDescription itself as a PyNN
 * script
 */
public class DirectPyNNJobParametersFactory implements JobParametersFactory {

    private static final String SCRIPT_NAME = "run.py";

    @Override
    public JobParameters getJobParameters(Job job, File workingDirectory)
            throws UnsupportedJobException, JobParametersFactoryException {
        if (!job.getExperimentDescription().contains("import")) {
            throw new UnsupportedJobException();
        }
        try {
            File scriptFile = new File(workingDirectory, SCRIPT_NAME);
            PrintWriter writer = new PrintWriter(scriptFile, "UTF-8");
            writer.print(job.getExperimentDescription());
            writer.close();

            return new PyNNJobParameters(workingDirectory.getAbsolutePath(),
                    SCRIPT_NAME + SYSTEM_ARG, job.getHardwareConfig());
        } catch (IOException e) {
            throw new JobParametersFactoryException("Error storing script", e);
        } catch (Throwable e) {
            throw new JobParametersFactoryException(
                "General error with PyNN Script", e);
        }
    }

}
