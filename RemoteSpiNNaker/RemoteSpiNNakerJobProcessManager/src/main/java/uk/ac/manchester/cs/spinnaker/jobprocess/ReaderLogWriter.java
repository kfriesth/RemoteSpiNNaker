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

	private boolean isRunning = false;

	private boolean isWriting = false;

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
		synchronized (this) {
		    isRunning = true;
		}
		while (!done) {
			try {
				String line = reader.readLine();
				if (line != null) {
					synchronized (this) {
						isWriting = true;
				        writer.append(line + "\n");
				        isWriting = false;
				        notifyAll();
					}
				}
			} catch (IOException e) {
				done = true;
			}
		}
		synchronized (this) {
		    isRunning = false;
		    notifyAll();
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
		synchronized (this) {
			System.err.println("Waiting for log writer to exit...");
			while (isRunning || isWriting) {
				try {
					wait();
				} catch (InterruptedException e) {

					// Does Nothing
				}
			}
			System.err.println("Log writer has exited");
		}
	}
}
