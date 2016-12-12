package uk.ac.manchester.cs.spinnaker.utils;

import static java.io.File.createTempFile;
import static org.apache.commons.io.FileUtils.forceDelete;
import static org.apache.commons.io.FileUtils.forceMkdir;

import java.io.File;
import java.io.IOException;

public class DirectoryUtils {
	public static File mktmpdir() {
		do {
			try {
				File tmp = createTempFile("jobOutput", ".tmp");
				forceDelete(tmp);
				forceMkdir(tmp);
				return tmp;
			} catch (IOException e) {
			}
		} while (true);
	}
}
