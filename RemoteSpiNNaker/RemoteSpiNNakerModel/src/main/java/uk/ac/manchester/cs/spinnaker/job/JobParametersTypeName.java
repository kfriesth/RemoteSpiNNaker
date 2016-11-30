package uk.ac.manchester.cs.spinnaker.job;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Identifies the unique type name of a {@link JobParameters} implementation.
 * Required if the parameters are to be serialized or deserialized.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface JobParametersTypeName {
	/**
	 * The type name
	 * 
	 * @return The type name
	 */
	String value();
}
