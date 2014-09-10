package uk.ac.manchester.cs.spinnaker.job;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies the unique type name of a JobParameters implementation.
 * Required if the parameters are to be serialized or deserialized.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JobParametersTypeName {

	/**
	 * The type name
	 * @return The type name
	 */
	String value();
}
