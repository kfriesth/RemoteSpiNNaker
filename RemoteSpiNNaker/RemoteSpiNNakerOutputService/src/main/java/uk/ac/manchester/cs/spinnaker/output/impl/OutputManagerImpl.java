package uk.ac.manchester.cs.spinnaker.output.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.output.OutputManager;
import uk.ac.manchester.cs.spinnaker.unicore.UnicoreFileManager;

public class OutputManagerImpl implements OutputManager {

    private File resultsDirectory = null;

    private URL baseServerUrl = null;

    /**
     * The logger
     */
    private Log logger = LogFactory.getLog(getClass());

    public OutputManagerImpl(URL baseServerUrl, File resultsDirectory) {
        this.baseServerUrl = baseServerUrl;
        this.resultsDirectory = resultsDirectory;
    }

    @Override
    public List<DataItem> addOutputs(
            String projectId, int id, File baseDirectory, List<File> outputs)
            throws IOException {
        if (outputs != null) {
            String pId = new File(projectId).getName();
            int pathStart = baseDirectory.getAbsolutePath().length();
            File projectDirectory = new File(resultsDirectory, pId);
            File idDirectory = new File(projectDirectory, String.valueOf(id));
            List<DataItem> outputData = new ArrayList<DataItem>();
            for (File output : outputs) {
                if (!output.getAbsolutePath().startsWith(
                        baseDirectory.getAbsolutePath())) {
                    throw new IOException(
                        "Output file " + output +
                        " is outside base directory " + baseDirectory);
                }

                String outputPath = output.getAbsolutePath().substring(
                    pathStart).replace('\\', '/');
                if (outputPath.startsWith("/")) {
                    outputPath = outputPath.substring(1);
                }
                File newOutput = new File(idDirectory, outputPath);
                newOutput.getParentFile().mkdirs();
                output.renameTo(newOutput);
                URL outputUrl = new URL(
                    baseServerUrl,
                    "output/" + pId + "/" + id + "/" + outputPath);
                outputData.add(new DataItem(outputUrl.toExternalForm()));
                logger.debug(
                    "New output " + newOutput + " mapped to " + outputUrl);
            }

            return outputData;
        }
        return null;
    }

    @Override
    public Response getResultFile(
            String projectId, int id, String filename, boolean download) {
        logger.debug(
            "Retrieving " + filename + " from " + projectId + "/" + id);
        File projectDirectory = new File(resultsDirectory, projectId);
        File idDirectory = new File(projectDirectory, String.valueOf(id));
        File resultFile = new File(idDirectory, filename);

        if (!resultFile.canRead()) {
            logger.debug(resultFile + " was not found");
            return Response.status(Status.NOT_FOUND).build();
        }

        if (!download) {
            try {
                String contentType = Files.probeContentType(
                    resultFile.toPath());
                if (contentType != null) {
                    logger.debug("File has content type " + contentType);
                    return Response.ok(resultFile, contentType).build();
                }
            } catch (IOException e) {
                logger.debug(
                    "Content type of " + resultFile +
                    " could not be determined", e);
            }
        }

        return Response.ok((Object) resultFile)
                .header("Content-Disposition",
                        "attachment; filename=" + filename)
                .build();
    }

    private void recursivelyUploadFiles(
            File directory, UnicoreFileManager fileManager, String storageId,
            String filePath) throws IOException {
        for (File file : directory.listFiles()) {
            String uploadFileName = filePath + "/" + file.getName();
            if (file.isDirectory()) {
                recursivelyUploadFiles(
                    file, fileManager, storageId, uploadFileName);
            } else {
                FileInputStream input = new FileInputStream(file);
                try {
                    fileManager.uploadFile(storageId, uploadFileName, input);
                } finally {
                    input.close();
                }
            }
        }
    }

    @Override
    public Response uploadResultsToHPCServer(
            String projectId, int id, String serverUrl, String storageId,
            String filePath, String userId, String token) {
        try {
            URL url = new URL(serverUrl);
            UnicoreFileManager fileManager = new UnicoreFileManager(
                url, userId, token);
            File projectDirectory = new File(resultsDirectory, projectId);
            File idDirectory = new File(projectDirectory, String.valueOf(id));
            if (!idDirectory.canRead()) {
                logger.debug(idDirectory + " was not found");
                return Response.status(Status.NOT_FOUND).build();
            }
            String uploadFilePath = filePath;
            if (uploadFilePath.endsWith("/")) {
                uploadFilePath = uploadFilePath.substring(
                    0, uploadFilePath.length() - 1);
            }
            recursivelyUploadFiles(
                idDirectory, fileManager, storageId, uploadFilePath);
            return Response.ok().build();
        } catch (MalformedURLException e) {
            logger.error(e);
            return Response.status(Status.BAD_REQUEST).entity(
                "The URL specified was malformed").build();
        } catch (IOException e) {
            logger.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(
                "General error reading or uploading a file").build();
        } catch (Throwable e) {
            logger.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

}
