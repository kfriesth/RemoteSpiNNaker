package uk.ac.manchester.cs.spinnaker.job_parameters.impl;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.CompressionType;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.job_parameters.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.job_parameters.UnsupportedJobException;
import uk.ac.manchester.cs.spinnaker.jobprocessmanager.FileDownloader;

/**
 * A JobParametersFactory that downloads a PyNN job as a zip or tar.gz file
 */
public class ZipPyNNJobParametersFactory implements JobParametersFactory {

    private static final String DEFAULT_SCRIPT_NAME = "run.py";

    @Override
    public JobParameters getJobParameters(Job job, File workingDirectory)
            throws UnsupportedJobException, JobParametersFactoryException {

        // Test that there is a URL
        String experimentDescription = job.getCode().trim();
        if (!experimentDescription.startsWith("http://")
                && !experimentDescription.startsWith("https://")) {
            throw new UnsupportedJobException();
        }

        // Test that the URL is well formed
        URL url = null;
        try {
            url = new URL(experimentDescription);
        } catch (MalformedURLException e) {
            throw new JobParametersFactoryException("The URL is malformed", e);
        }

        // Try to get the file and extract it
        try {
            File output = FileDownloader.downloadFile(
                url, workingDirectory, null);

            // Test if there is a recognised archive
            Archiver archiver = null;
            boolean archiveExtracted = false;
            try {
                archiver = ArchiverFactory.createArchiver(output);
                archiver.extract(output, workingDirectory);
                archiveExtracted = true;
            } catch (IllegalArgumentException e) {

                // Ignore - will be handled next
            }

            // If the archive wasn't extracted by the last line, try the
            // known formats
            for (ArchiveFormat format : ArchiveFormat.values()) {
                try {
                    archiver = ArchiverFactory.createArchiver(format);
                    archiver.extract(output, workingDirectory);
                    archiveExtracted = true;
                    break;
                } catch (IOException e) {

                    // Ignore - try the next
                }
            }

            // If the archive was still not extracted, try again with
            // different compression types
            if (!archiveExtracted) {
                CompressionType[] typesSupported = new CompressionType[]{
                    CompressionType.BZIP2, CompressionType.GZIP
                };
                for (ArchiveFormat format : ArchiveFormat.values()) {
                    for (CompressionType type : typesSupported) {
                        try {
                            archiver = ArchiverFactory.createArchiver(
                                format, type);
                            archiver.extract(output, workingDirectory);
                            archiveExtracted = true;
                            break;
                        } catch (IOException e) {

                            // Ignore - try the next
                        }
                    }
                }
            }

            // Delete the archive
            if (!output.delete()) {
                System.err.println("Warning, could not delete file " + output);
            }

            // If the archive wasn't extracted, throw an error
            if (!archiveExtracted) {
                throw new JobParametersFactoryException(
                    "The URL could not be decompressed with any known method");
            }

            String script = DEFAULT_SCRIPT_NAME + SYSTEM_ARG;
            String command = job.getCommand();
            if (command != null && !command.equals("")) {
                script = command;
            }

            PyNNJobParameters parameters = new PyNNJobParameters(
                    workingDirectory.getAbsolutePath(), script,
                    job.getHardwareConfig());
            return parameters;
        } catch (IOException e) {
            e.printStackTrace();
            throw new JobParametersFactoryException(
                "Error in communication or extraction", e);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new JobParametersFactoryException(
                "General error with zip extraction", e);
        }
    }
}
