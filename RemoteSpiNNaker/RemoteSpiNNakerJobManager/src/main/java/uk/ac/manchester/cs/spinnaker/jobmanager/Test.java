package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.File;

public class Test {
	public static void main(String[] args) {
		System.err.println(System.getProperty("java.home"));
		File binDir = new File(System.getProperty("java.home"), "bin");
		File exec = new File(binDir, "java");
		if (!exec.canExecute()) {
			exec = new File(binDir, "java.exe");
		}

		System.err.println("Java at " + exec + " exists?" + exec.canExecute());
	}

}
