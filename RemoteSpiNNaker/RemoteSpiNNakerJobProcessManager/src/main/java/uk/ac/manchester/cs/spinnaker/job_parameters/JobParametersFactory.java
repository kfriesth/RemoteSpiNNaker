package uk.ac.manchester.cs.spinnaker.job_parameters;

import java.io.File;
import java.util.Map;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;

/**
 * A factory that produces job parameters
 */
public abstract class JobParametersFactory {
	/**
	 * The argument to append to the script name to request that the system is
	 * added to the command line.
	 */
	// NB has a space at the start
	public static final String SYSTEM_ARG = " {system}";

	/** The default name of the python script that will be constructed. */
	public static final String DEFAULT_SCRIPT_NAME = "run.py";

	/**
	 * Gets job parameters given job description data.
	 * 
	 * @param job
	 *            The job to be executed
	 * @param workingDirectory
	 *            The working directory where the job will be run
	 * @return A job description to be executed
	 * @throws UnsupportedJobException
	 *             If the factory does not support the job
	 * @throws JobParametersFactoryException
	 *             If there was an error getting the parameters
	 */
	public abstract JobParameters getJobParameters(Job job, File workingDirectory)
			throws UnsupportedJobException, JobParametersFactoryException;

	/** The factories for converting jobs into parameters. */
	private static final JobParametersFactory[] JOB_PARAMETER_FACTORIES = new JobParametersFactory[] {
			new GitPyNNJobParametersFactory(),
			new ZipPyNNJobParametersFactory(),
			new DirectPyNNJobParametersFactory() };

	public static JobParameters getJobParameters(Job job, File workingDirectory,
			Map<String, JobParametersFactoryException> errors) {
		for (JobParametersFactory factory : JOB_PARAMETER_FACTORIES)
			try {
				JobParameters parameters = factory.getJobParameters(job,
						workingDirectory);
				if (parameters != null)
					return parameters;
			} catch (UnsupportedJobException e) {
				// Do Nothing
			} catch (JobParametersFactoryException e) {
				errors.put(factory.getClass().getSimpleName(), e);
			}
		return null;
	}
}
