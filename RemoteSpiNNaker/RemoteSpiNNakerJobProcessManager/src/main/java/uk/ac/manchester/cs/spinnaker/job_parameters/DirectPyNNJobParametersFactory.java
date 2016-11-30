package uk.ac.manchester.cs.spinnaker.job_parameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job.pynn.PyNNJobParameters;

/**
 * A {@link JobParametersFactory} that uses the <tt>experimentDescription</tt>
 * itself as a PyNN script.
 */
class DirectPyNNJobParametersFactory extends JobParametersFactory {
	private static final String ENCODING = "UTF-8";

	@Override
	public JobParameters getJobParameters(Job job, File workingDirectory)
			throws UnsupportedJobException, JobParametersFactoryException {
		if (!job.getCode().contains("import"))
			throw new UnsupportedJobException();

		try {
			return constructParameters(job, workingDirectory);
		} catch (IOException e) {
			throw new JobParametersFactoryException("Error storing script", e);
		} catch (Throwable e) {
			throw new JobParametersFactoryException(
					"General error with PyNN Script", e);
		}
	}

	/** Constructs the parameters by writing the script into a local file. */
	private JobParameters constructParameters(Job job, File workingDirectory)
			throws FileNotFoundException, UnsupportedEncodingException {
		File scriptFile = new File(workingDirectory, DEFAULT_SCRIPT_NAME);
		PrintWriter writer = new PrintWriter(scriptFile, ENCODING);
		writer.print(job.getCode());
		writer.close();

		return new PyNNJobParameters(workingDirectory.getAbsolutePath(),
				DEFAULT_SCRIPT_NAME + SYSTEM_ARG, job.getHardwareConfig());
	}

}
