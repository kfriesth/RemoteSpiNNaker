package uk.ac.manchester.cs.spinnaker.jobmanager.jobparametersfactories;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.CompressionType;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.jobmanager.FileDownloader;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobParametersFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobParametersFactoryException;
import uk.ac.manchester.cs.spinnaker.jobmanager.UnsupportedJobException;

/**
 * A JobParametersFactory that downloads a PyNN job as a zip or tar.gz file
 */
public class ZipPyNNJobParametersFactory implements JobParametersFactory {

    private static final String DEFAULT_SCRIPT_NAME = "run.py";

    @Override
    public JobParameters getJobParameters(String experimentDescription,
            String command, List<String> inputData,
            Map<String, Object> hardwareConfiguration,
            File workingDirectory, boolean deleteJobOnExit)
            throws UnsupportedJobException, JobParametersFactoryException {

        // Test that there is a URL
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
            // compressors
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
            output.delete();

            // If the archive wasn't extracted, throw an error
            if (!archiveExtracted) {
                throw new JobParametersFactoryException(
                    "The URL could not be decompressed with any known method");
            }

            String script = DEFAULT_SCRIPT_NAME + SYSTEM_ARG;
            if (command != null && !command.equals("")) {
                script = command;
            }

            PyNNJobParameters parameters = new PyNNJobParameters(
                    workingDirectory.getAbsolutePath(), script,
                    hardwareConfiguration, deleteJobOnExit);
            return parameters;
        } catch (IOException e) {
            throw new JobParametersFactoryException(
                "Error in communication or extraction", e);
        } catch (Throwable e) {
            throw new JobParametersFactoryException(
                "General error with zip extraction", e);
        }
    }
}
