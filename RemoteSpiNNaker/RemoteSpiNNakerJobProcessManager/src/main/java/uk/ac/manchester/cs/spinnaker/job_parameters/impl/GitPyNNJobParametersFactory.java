package uk.ac.manchester.cs.spinnaker.job_parameters.impl;

import java.io.File;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.job_parameters.UnsupportedJobException;

/**
 * A JobParametersFactory that downloads a PyNN job from git
 */
public class GitPyNNJobParametersFactory implements JobParametersFactory {

    private static final String DEFAULT_SCRIPT_NAME = "run.py";

    @Override
    public JobParameters getJobParameters(Job job, File workingDirectory)
            throws UnsupportedJobException, JobParametersFactoryException {

        // Test that there is a URL
        String experimentDescription = job.getCode().trim();
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
            String command = job.getCommand();
            if (command != null && !command.equals("")) {
                script = command;
            }

            PyNNJobParameters parameters = new PyNNJobParameters(
                    workingDirectory.getAbsolutePath(), script,
                    job.getHardwareConfig());
            return parameters;
        } catch (InvalidRemoteException e) {
            throw new JobParametersFactoryException("Remote is not valid", e);
        } catch (TransportException e) {
            throw new JobParametersFactoryException("Transport failed", e);
        } catch (GitAPIException e) {
            throw new JobParametersFactoryException("Error using Git", e);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new JobParametersFactoryException(
                "General error getting git repository", e);
        }
    }
}
