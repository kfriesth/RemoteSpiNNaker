package uk.ac.manchester.cs.spinnaker.jobmanager.jobparametersfactories;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.jobmanager.UnsupportedJobException;

/**
 * A JobParametersFactory that downloads a PyNN job from github
 */
public class GitPyNNJobParametersFactory implements JobParametersFactory {

    private static final String DEFAULT_SCRIPT_NAME = "run.py";

    @Override
    public JobParameters getJobParameters(String experimentDescription,
            String command, List<String> inputData,
            Map<String, Object> hardwareConfiguration,
            File workingDirectory, boolean deleteJobOnExit)
            throws UnsupportedJobException, JobParametersFactoryException {

        // Test that there is a URL
        if (!experimentDescription.startsWith("http://")
                && !experimentDescription.startsWith("https://")) {
            throw new UnsupportedJobException();
        }

        // Try to get the repository
        try {
            CloneCommand clone = Git.cloneRepository();
            clone.setURI(experimentDescription);
            clone.setDirectory(workingDirectory);
            clone.setCloneSubmodules(true);
            clone.call();

            String script = DEFAULT_SCRIPT_NAME + SYSTEM_ARG;
            if (command != null && !command.equals("")) {
                script = command;
            }

            PyNNJobParameters parameters = new PyNNJobParameters(
                    workingDirectory.getAbsolutePath(), script,
                    hardwareConfiguration, deleteJobOnExit);
            return parameters;
        } catch (InvalidRemoteException e) {
            throw new JobParametersFactoryException("Remote is not valid", e);
        } catch (TransportException e) {
            throw new JobParametersFactoryException("Transport failed", e);
        } catch (GitAPIException e) {
            throw new JobParametersFactoryException("Error using Git", e);
        } catch (Throwable e) {
            throw new JobParametersFactoryException(
                "General error getting git repository", e);
        }
    }
}
