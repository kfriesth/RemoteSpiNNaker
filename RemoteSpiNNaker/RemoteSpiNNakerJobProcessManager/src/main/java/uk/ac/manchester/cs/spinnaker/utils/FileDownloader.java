package uk.ac.manchester.cs.spinnaker.utils;

import static java.io.File.createTempFile;
import static java.nio.file.Files.copy;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import org.jboss.resteasy.util.ParameterParser;

public class FileDownloader {
	private static String getFileName(String contentDisposition) {
		if (contentDisposition != null) {
			String cdl = contentDisposition.toLowerCase();
			if (cdl.startsWith("form-data") || cdl.startsWith("attachment")) {
				ParameterParser parser = new ParameterParser();
				parser.setLowerCaseNames(true);
				Map<String, String> params = parser.parse(contentDisposition,
						';');
				if (params.containsKey("filename"))
					return params.get("filename").trim();
			}
		}
		return null;
	}

	/**
	 * Downloads a file from a URL.
	 * 
	 * @param url
	 *            The url to download the file from
	 * @param workingDirectory
	 *            The directory to output the file to
	 * @param defaultFilename
	 *            The name of the file to use if none can be worked out from the
	 *            url or headers, or null to use a generated name
	 * @return The file downloaded
	 * @throws IOException
	 */
	public static File downloadFile(URL url, File workingDirectory,
			String defaultFilename) throws IOException {
		// Open a connection
		URLConnection urlConnection = url.openConnection();
		urlConnection.setDoInput(true);

		// Work out the output filename
		String filename = getFileName(urlConnection
				.getHeaderField("Content-Disposition"));
		File output;
		if (filename != null)
			output = new File(workingDirectory, filename);
		else {
			if (defaultFilename != null) {
				output = new File(workingDirectory, defaultFilename);
			} else if (!url.getPath().isEmpty()) {
				output = new File(workingDirectory,
						new File(url.getPath()).getName());
			} else {
				output = createTempFile("download", "file", workingDirectory);
			}
		}

		// Write the file
		copy(urlConnection.getInputStream(), output.toPath());

		return output;
	}

	/**
	 * Downloads a file from a URL.
	 * 
	 * @param url
	 *            The url to download the file from
	 * @param workingDirectory
	 *            The directory to output the file to
	 * @param defaultFilename
	 *            The name of the file to use if none can be worked out from the
	 *            url or headers, or null to use a generated name
	 * @return The file downloaded
	 * @throws IOException
	 */
	public static File downloadFile(String url, File workingDirectory,
			String defaultFilename) throws IOException {
		return downloadFile(new URL(url), workingDirectory, defaultFilename);
	}
}
