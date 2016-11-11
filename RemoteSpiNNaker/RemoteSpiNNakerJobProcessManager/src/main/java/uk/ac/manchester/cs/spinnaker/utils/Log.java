package uk.ac.manchester.cs.spinnaker.utils;

public class Log {
	public static void log(String message) {
		System.err.println(message);
	}

	public static void log(Throwable exception) {
		exception.printStackTrace(System.err);
	}
}
