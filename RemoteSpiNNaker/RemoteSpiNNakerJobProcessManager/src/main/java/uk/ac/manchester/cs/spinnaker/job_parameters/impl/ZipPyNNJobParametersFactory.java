package uk.ac.manchester.cs.spinnaker.job_parameters.impl;

import static org.rauschig.jarchivelib.ArchiverFactory.createArchiver;
import static org.rauschig.jarchivelib.CompressionType.BZIP2;
import static org.rauschig.jarchivelib.CompressionType.GZIP;
import static uk.ac.manchester.cs.spinnaker.utils.FileDownloader.downloadFile;
import static uk.ac.manchester.cs.spinnaker.utils.Log.log;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.CompressionType;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.job_parameters.UnsupportedJobException;

/**
 * A {@link JobParametersFactory} that downloads a PyNN job as a zip or tar.gz
 * file. The URL must refer to a world-readable URL or the credentials must be
 * present in the URL.
 */
public class ZipPyNNJobParametersFactory implements JobParametersFactory {
	@Override
	public JobParameters getJobParameters(Job job, File workingDirectory)
			throws UnsupportedJobException, JobParametersFactoryException {
		// Test that there is a URL
		String jobCodeLocation = job.getCode().trim();
		if (!jobCodeLocation.startsWith("http://")
				&& !jobCodeLocation.startsWith("https://"))
			throw new UnsupportedJobException();

		// Test that the URL is well formed
		URL url;
		try {
			url = new URL(jobCodeLocation);
		} catch (MalformedURLException e) {
			throw new JobParametersFactoryException("The URL is malformed", e);
		}

		// Try to get the file and extract it
		try {
			return constructParameters(job, workingDirectory, url);
		} catch (IOException e) {
			log(e);
			throw new JobParametersFactoryException(
					"Error in communication or extraction", e);
		} catch (Throwable e) {
			log(e);
			throw new JobParametersFactoryException(
					"General error with zip extraction", e);
		}
	}

	private static final CompressionType[] SUPPORTED_TYPES = new CompressionType[] {
			BZIP2, GZIP };

	private boolean extractAutodetectedArchive(File output,
			File workingDirectory) throws IOException {
		try {
			Archiver archiver = createArchiver(output);
			archiver.extract(output, workingDirectory);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private boolean extractArchiveUsingKnownFormats(File workingDirectory,
			File output) {
		for (ArchiveFormat format : ArchiveFormat.values())
			try {
				Archiver archiver = createArchiver(format);
				archiver.extract(output, workingDirectory);
				return true;
			} catch (IOException e) {
				// Ignore - try the next
			}
		return false;
	}

	private boolean extractTypedArchive(File workingDirectory, File output) {
		for (ArchiveFormat format : ArchiveFormat.values())
			for (CompressionType type : SUPPORTED_TYPES)
				try {
					Archiver archiver = createArchiver(format, type);
					archiver.extract(output, workingDirectory);
					return true;
				} catch (IOException e) {
					// Ignore - try the next
				}
		return false;
	}

	private JobParameters constructParameters(Job job, File workingDirectory,
			URL url) throws IOException, JobParametersFactoryException {
		File output = downloadFile(url, workingDirectory, null);

		/* Test if there is a recognised archive */
		boolean archiveExtracted = extractAutodetectedArchive(output,
				workingDirectory);

		/*
		 * If the archive wasn't extracted by the last line, try the known
		 * formats
		 */
		if (!archiveExtracted)
			archiveExtracted = extractArchiveUsingKnownFormats(
					workingDirectory, output);

		/*
		 * If the archive was still not extracted, try again with different
		 * compression types
		 */
		if (!archiveExtracted)
			archiveExtracted = extractTypedArchive(workingDirectory, output);

		// Delete the archive
		if (!output.delete())
			log("Warning, could not delete file " + output);

		// If the archive wasn't extracted, throw an error
		if (!archiveExtracted)
			throw new JobParametersFactoryException(
					"The URL could not be decompressed with any known method");

		String script = DEFAULT_SCRIPT_NAME + SYSTEM_ARG;
		String command = job.getCommand();
		if (command != null && !command.isEmpty())
			script = command;

		return new PyNNJobParameters(workingDirectory.getAbsolutePath(),
				script, job.getHardwareConfig());
	}
}
