package uk.ac.manchester.cs.spinnaker.jobmanager.jobparametersfactories;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.jobmanager.UnsupportedJobException;

/**
 * A JobParametersFactory that uses the experimentDescription itself as a PyNN
 * script
 */
public class DirectPyNNJobParametersFactory implements JobParametersFactory {

    private static final String SCRIPT_NAME = "run.py";

    @Override
    public JobParameters getJobParameters(String experimentDescription,
            List<String> inputData, Map<String, Object> hardwareConfiguration,
            File workingDirectory, boolean deleteJobOnExit)
            throws UnsupportedJobException, JobParametersFactoryException {
        if (!experimentDescription.contains("import")) {
            throw new UnsupportedJobException();
        }
        try {

            File scriptFile = new File(workingDirectory, SCRIPT_NAME);
            PrintWriter writer = new PrintWriter(scriptFile, "UTF-8");
            writer.print(experimentDescription);
            writer.close();

            return new PyNNJobParameters(workingDirectory.getAbsolutePath(),
                    SCRIPT_NAME, hardwareConfiguration, deleteJobOnExit);
        } catch (IOException e) {
            throw new JobParametersFactoryException("Error storing script", e);
        } catch (Throwable e) {
            throw new JobParametersFactoryException(
                "General error with PyNN Script", e);
        }
    }

}
