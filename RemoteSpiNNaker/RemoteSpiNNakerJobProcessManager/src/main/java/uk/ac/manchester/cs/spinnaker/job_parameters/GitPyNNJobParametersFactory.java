package uk.ac.manchester.cs.spinnaker.job_parameters;

import static org.eclipse.jgit.api.Git.cloneRepository;

import java.io.File;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;

/**
 * A {@link JobParametersFactory} that downloads a PyNN job from git. The git
 * repository must be world-readable, or sufficient credentials must be present
 * in the URL.
 */
class GitPyNNJobParametersFactory extends JobParametersFactory {
	@Override
	public JobParameters getJobParameters(Job job, File workingDirectory)
			throws UnsupportedJobException, JobParametersFactoryException {
		// Test that there is a URL
		String jobCodeLocation = job.getCode().trim();
		if (!jobCodeLocation.startsWith("http://")
				&& !jobCodeLocation.startsWith("https://"))
			throw new UnsupportedJobException();

		// Try to get the repository
		try {
			return constructParameters(job, workingDirectory, jobCodeLocation);
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

	/** Constructs the parameters by checking out the git repository. */
	private JobParameters constructParameters(Job job, File workingDirectory,
			String experimentDescription) throws GitAPIException,
			InvalidRemoteException, TransportException {
		CloneCommand clone = cloneRepository();
		clone.setURI(experimentDescription);
		clone.setDirectory(workingDirectory);
		clone.setCloneSubmodules(true);
		clone.call();

		String script = DEFAULT_SCRIPT_NAME + SYSTEM_ARG;
		String command = job.getCommand();
		if (command != null && !command.isEmpty())
			script = command;

		return new PyNNJobParameters(workingDirectory.getAbsolutePath(),
				script, job.getHardwareConfig());
	}
}
