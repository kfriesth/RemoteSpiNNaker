package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class JobOutputPipe extends Thread {

	private BufferedReader reader = null;

	private PrintWriter writer = null;

	private boolean done;

	private Log logger = LogFactory.getLog(getClass());

	public JobOutputPipe(InputStream input, File output)
			throws FileNotFoundException {
		reader = new BufferedReader(new InputStreamReader(input));
		writer = new PrintWriter(output);
	}

	public void run() {
		String line = null;
		try {
			line = reader.readLine();
		} catch (IOException e) {
			done = true;
		}
		while (!done && (line != null)) {
			try {
				if (!line.isEmpty()) {
				    logger.debug(line);
				    writer.println(line);
				}
				line = reader.readLine();
			} catch (IOException e) {
				done = true;
			}
		}
		writer.close();
	}

	public void close() {
		done = true;
		try {
			reader.close();
		} catch (IOException e) {
			// Does Nothing
		}
	}
}
