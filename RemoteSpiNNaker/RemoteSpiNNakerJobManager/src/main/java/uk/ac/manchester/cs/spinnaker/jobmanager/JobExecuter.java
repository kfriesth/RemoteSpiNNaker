package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Executes jobs in an external process
 *
 */
public class JobExecuter {

	private static File getJavaExec() throws IOException {
		File binDir = new File(System.getProperty("java.home"), "bin");
		File exec = new File(binDir, "java");
		if (!exec.canExecute()) {
			exec = new File(binDir, "java.exe");
		}
		return exec;
	}

	private File javaExec = null;

	public JobExecuter(File javaExec, List<File> classPath, String mainClass)
			throws IOException {
		this.javaExec = javaExec;

	}
}
