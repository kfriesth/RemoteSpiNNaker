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
	private final BufferedReader reader;
	private final PrintWriter writer;
	private volatile boolean done;
	private Log logger = LogFactory.getLog(getClass());

	public JobOutputPipe(InputStream input, File output)
			throws FileNotFoundException {
		reader = new BufferedReader(new InputStreamReader(input));
		writer = new PrintWriter(output);
		done = false;
		setDaemon(true);
	}

	@Override
	public void run() {
		while (!done) {
			String line = null;
			try {
				line = reader.readLine();
			} catch (IOException e) {
				break;
			}
			if (line == null)
				break;
			if (!line.isEmpty()) {
				logger.debug(line);
				writer.println(line);
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
