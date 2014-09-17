package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class UnclosableOutputStream extends FilterOutputStream {

	public UnclosableOutputStream(OutputStream output) {
		super(output);
	}

	@Override
	public void close() throws IOException {

		// Does Nothing to avoid closing
	}

}
