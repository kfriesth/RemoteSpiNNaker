package uk.ac.manchester.cs.spinnaker.jobprocess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * A logger that pushes everything from a reader to a {@link LogWriter}.
 */
public class ReaderLogWriter extends Thread implements AutoCloseable {
    private BufferedReader reader;
    private LogWriter writer;

    private boolean done;
    private boolean isRunning;
    private boolean isWriting;

	/**
	 * Creates a new ReaderLogWriter with another reader.
	 * 
	 * @param reader
	 *            The reader to read from
	 * @param writer
	 *            The writer to write to
	 */
    public ReaderLogWriter(Reader reader, LogWriter writer) {
        if (reader instanceof BufferedReader) {
            this.reader = (BufferedReader) reader;
        } else {
            this.reader = new BufferedReader(reader);
        }
        this.writer = writer;
        this.setDaemon(true);
    }

    /**
	 * Creates a new ReaderLogWriter with an input stream. This will be treated
	 * as a text stream using the system encoding.
	 * 
	 * @param input
	 *            The input stream to read from.
	 * @param writer
	 *            The writer to write to.
	 */
    public ReaderLogWriter(InputStream input, LogWriter writer) {
        this(new InputStreamReader(input), writer);
    }

    @Override
    public void run() {
        synchronized (this) {
            isRunning = true;
        }
		try {
			copyStream();
		} finally {
			synchronized (this) {
				isRunning = false;
				notifyAll();
			}
		}
    }

	private void copyStream() {
		while (!done) {
            try {
                String line = reader.readLine();
                if (line == null)
                	return;
				synchronized (this) {
					isWriting = true;
					writer.append(line + "\n");
					isWriting = false;
					notifyAll();
				}
            } catch (IOException e) {
                done = true;
            }
        }
	}

	/**
	 * Closes the reader/writer
	 */
    @Override
	public void close() {
        synchronized (this) {
            System.err.println("Waiting for log writer to exit...");
            while (isRunning || isWriting)
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Does Nothing
                }
            System.err.println("Log writer has exited");
        }

        try {
            reader.close();
        } catch (IOException e) {
            // Do Nothing
        }
    }
}
