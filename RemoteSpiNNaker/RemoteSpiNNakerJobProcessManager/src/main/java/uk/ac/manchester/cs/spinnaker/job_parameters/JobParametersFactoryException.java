package uk.ac.manchester.cs.spinnaker.job_parameters;

/**
 * Indicates that whilst the job type was supported, there was an error
 * converting the job to parameters.
 */
public class JobParametersFactoryException extends Exception {
	private static final long serialVersionUID = 1L;

	public JobParametersFactoryException(String message) {
		super(message);
	}

	public JobParametersFactoryException(String message, Throwable cause) {
		super(message, cause);
	}
}
