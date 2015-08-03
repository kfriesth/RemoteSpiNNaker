package uk.ac.manchester.cs.spinnaker.jobmanager.jobparametersfactories;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
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
            List<String> inputData, Map<String, Object> hardwareConfiguration,
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

        // Test if there is a recognised archive
        File inputPath = new File(url.getPath());
        Archiver archiver = null;
        try {
            archiver = ArchiverFactory.createArchiver(inputPath);
        } catch (IllegalArgumentException e) {

            // If this happens, the archive cannot be extracted
            throw new UnsupportedJobException();
        }

        // Try to get the file and extract it
        try {
            URLConnection urlConnection = url.openConnection();
            urlConnection.setDoOutput(true);
            File output = new File(workingDirectory, inputPath.getName());
            Files.copy(urlConnection.getInputStream(), output.toPath());
            archiver.extract(output, workingDirectory);
            output.delete();

            File scriptFile = new File(workingDirectory,
                    DEFAULT_SCRIPT_NAME);
            if (!scriptFile.exists()) {
                deleteDirectory(workingDirectory);
                throw new JobParametersFactoryException(
                        "Repository doesn't appear to contain a script called "
                        + DEFAULT_SCRIPT_NAME);
            }

            PyNNJobParameters parameters = new PyNNJobParameters(
                    workingDirectory.getAbsolutePath(), DEFAULT_SCRIPT_NAME,
                    hardwareConfiguration, deleteJobOnExit);
            return parameters;
        } catch (IOException e) {
            throw new JobParametersFactoryException(
                "Error in communication or extraction", e);
        }

    }

    private void deleteDirectory(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                deleteDirectory(file);
            } else {
                file.delete();
            }
        }
        directory.delete();
    }

}
