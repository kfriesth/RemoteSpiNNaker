package uk.ac.manchester.cs.spinnaker.jobprocess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * A logger that pushes everything from a reader to a LogWriter
 */
public class ReaderLogWriter extends Thread {

	private BufferedReader reader = null;

	private LogWriter writer = null;

	private boolean done = false;

	/**
	 * Creates a new ReaderLogWriter with another reader
	 * @param reader The reader to read from
	 * @param writer The writer to write to
	 */
	public ReaderLogWriter(Reader reader, LogWriter writer) {
		if (reader instanceof BufferedReader) {
			this.reader = (BufferedReader) reader;
		} else {
			this.reader = new BufferedReader(reader);
		}
		this.writer = writer;
	}

	/**
	 * Creates a new ReaderLogWriter with an input stream
	 * @param input The input stream to read from
	 * @param writer The writer to write to
	 */
	public ReaderLogWriter(InputStream input, LogWriter writer) {
		this(new InputStreamReader(input), writer);
	}

	@Override
	public void run() {
		while (!done) {
			try {
				String line = reader.readLine();
				writer.append(line + "\n");
			} catch (IOException e) {
				done = true;
			}
		}
	}

	/**
	 * Closes the reader/writer
	 */
	public void close() {
		done = true;
		try {
			reader.close();
		} catch (IOException e) {

			// Do Nothing
		}
	}
}
