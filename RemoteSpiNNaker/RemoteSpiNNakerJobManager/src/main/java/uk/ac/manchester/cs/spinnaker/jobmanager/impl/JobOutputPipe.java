package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.slf4j.Logger;

public class JobOutputPipe extends Thread {
	private final BufferedReader reader;
	private final PrintWriter writer;
	private volatile boolean done;
	private Logger logger = getLogger(getClass());

	public JobOutputPipe(ThreadGroup threadGroup, InputStream input, File output)
			throws FileNotFoundException {
		super(threadGroup, "JobOutputPipe");
		reader = new BufferedReader(new InputStreamReader(input));
		writer = new PrintWriter(output);
		done = false;
		setDaemon(true);
	}

	@Override
	public void run() {
		while (!done) {
			String line;
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
		closeQuietly(reader);
	}
}
