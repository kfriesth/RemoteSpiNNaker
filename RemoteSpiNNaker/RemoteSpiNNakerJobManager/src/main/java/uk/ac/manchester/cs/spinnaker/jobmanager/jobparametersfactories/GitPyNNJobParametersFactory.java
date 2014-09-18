package uk.ac.manchester.cs.spinnaker.jobmanager.jobparametersfactories;

import java.io.File;
import java.util.List;

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
			List<String> inputData, String hardwareConfiguration,
			File workingDirectory)
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
		    clone.call();

		    File scriptFile = new File(workingDirectory,
		    		DEFAULT_SCRIPT_NAME);
		    if (!scriptFile.exists()) {
		    	deleteDirectory(workingDirectory);
		    	throw new JobParametersFactoryException(
		    			"Repository doesn't appear to contain a script called "
		    	        + DEFAULT_SCRIPT_NAME);
		    }

		    PyNNJobParameters parameters = new PyNNJobParameters(
		    		workingDirectory.getAbsolutePath(), DEFAULT_SCRIPT_NAME,
		    		true);
		    return parameters;
		} catch (InvalidRemoteException e) {
			throw new JobParametersFactoryException("Remote is not valid", e);
		} catch (TransportException e) {
			throw new JobParametersFactoryException("Transport failed", e);
		} catch (GitAPIException e) {
			throw new JobParametersFactoryException("Error using Git", e);
		}

	}

	private void deleteDirectory(File directory) {
		for (File file : directory.listFiles()) {
			if (file.isDirectory()) {
				deleteDirectory(file);
			} else {
				file.delete();
			}
		}
		directory.delete();
	}

}
