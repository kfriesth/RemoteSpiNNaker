package uk.ac.manchester.cs.spinnaker.utils;

import static java.io.File.createTempFile;
import static java.nio.file.Files.copy;
import static java.util.Objects.requireNonNull;

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
	 *            url or headers, or <tt>null</tt> to use a generated name
	 * @return The file downloaded
	 * @throws IOException
	 */
	public static File downloadFile(URL url, File workingDirectory,
			String defaultFilename) throws IOException {
		requireNonNull(workingDirectory);

		// Open a connection
		URLConnection urlConnection = requireNonNull(url).openConnection();
		urlConnection.setDoInput(true);

		// Work out the output filename
		File output = getTargetFile(url, workingDirectory, defaultFilename,
				urlConnection);

		// Write the file
		copy(urlConnection.getInputStream(), output.toPath());

		return output;
	}

	private static File getTargetFile(URL url, File workingDirectory,
			String defaultFilename, URLConnection urlConnection)
			throws IOException {
		String filename = getFileName(urlConnection
				.getHeaderField("Content-Disposition"));
		if (filename != null)
			return new File(workingDirectory, filename);
		if (defaultFilename != null)
			return new File(workingDirectory, defaultFilename);
		String path = url.getPath();
		if (path.isEmpty())
			return createTempFile("download", "file", workingDirectory);
		return new File(workingDirectory, new File(path).getName());
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
	 *            url or headers, or <tt>null</tt> to use a generated name
	 * @return The file downloaded
	 * @throws IOException
	 */
	public static File downloadFile(String url, File workingDirectory,
			String defaultFilename) throws IOException {
		return downloadFile(new URL(url), workingDirectory, defaultFilename);
	}
}
