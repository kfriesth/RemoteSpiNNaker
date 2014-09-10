package uk.ac.manchester.cs.spinnaker.jobprocess;

import java.util.Collection;
import java.util.HashMap;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;

/**
 * A factory for creating JobProcess instances given a JobParameters instance
 */
public class JobProcessFactory {

	/**
	 * A map between parameter types and process types.  Note that the type
	 * is guaranteed by the addMapping method, which is the only place that
	 * this map should be modified.
	 */
	private HashMap<Class<? extends JobParameters>,
	            Class<? extends JobProcess<? extends JobParameters>>> typeMap =
			new HashMap<Class<? extends JobParameters>,
			    Class<? extends JobProcess<? extends JobParameters>>>();

	/**
	 * Adds a new type mapping
	 * @param parameterType The job parameter type
	 * @param processType The job process type
	 */
	public <P extends JobParameters> void addMapping(Class<P> parameterType,
			Class<? extends JobProcess<P>> processType) {
		this.typeMap.put(parameterType, processType);
	}

	public Collection<Class<? extends JobParameters>> getParameterTypes() {
		return typeMap.keySet();
	}

	/**
	 * Creates a JobProcess given a JobParameters instance
	 *
	 * @param parameters The parameters of the job
	 * @return A JobProcess matching the parameters
	 * @throws IllegalAccessException If there is an error creating the class
	 * @throws InstantiationException If there is an error creating the class
	 */
	public <P extends JobParameters> JobProcess<P> createProcess(P parameters)
			throws InstantiationException, IllegalAccessException {

		// We know that this is of the correct type, because the addMapping
		// method will only allow the correct type mapping in
		@SuppressWarnings("unchecked")
		Class<JobProcess<P>> processType =
				(Class<JobProcess<P>>) typeMap.get(parameters.getClass());

		return processType.newInstance();
	}
}
