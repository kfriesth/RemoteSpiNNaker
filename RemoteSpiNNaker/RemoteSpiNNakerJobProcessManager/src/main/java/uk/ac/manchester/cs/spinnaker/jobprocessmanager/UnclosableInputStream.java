package uk.ac.manchester.cs.spinnaker.jobprocessmanager;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class UnclosableInputStream extends FilterInputStream {

	public UnclosableInputStream(InputStream input) {
		super(input);
	}

	@Override
	public void close() throws IOException {

		// Does Nothing - cannot be closed!
	}

}
