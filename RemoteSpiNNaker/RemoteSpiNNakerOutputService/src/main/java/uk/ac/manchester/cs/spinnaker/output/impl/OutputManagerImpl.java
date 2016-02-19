package uk.ac.manchester.cs.spinnaker.output.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.output.OutputManager;
import uk.ac.manchester.cs.spinnaker.unicore.UnicoreFileManager;

public class OutputManagerImpl implements OutputManager {

    private static final String PURGED_FILE = ".purged_";

    private File resultsDirectory = null;

    private URL baseServerUrl = null;

    private long timeToKeepResults = 0;

    private Map<File, Lock> synchronizers = new HashMap<File, Lock>();

    private Log logger = LogFactory.getLog(getClass());

    private class Lock {

        private boolean locked = true;

        private boolean waiting = false;

        private synchronized void waitForUnlock() {
            waiting = true;

            // Wait until unlocked
            while (locked) {
                try {
                    wait();
                } catch (InterruptedException e) {

                    // Do Nothing
                }
            }

            // Now lock again
            locked = true;
            waiting = false;
        }

        private synchronized boolean unlock() {
            locked = false;
            notifyAll();
            return waiting;
        }

    }

    public OutputManagerImpl(
            URL baseServerUrl, File resultsDirectory, long nDaysToKeepResults) {
        this.baseServerUrl = baseServerUrl;
        this.resultsDirectory = resultsDirectory;
        this.timeToKeepResults = TimeUnit.MILLISECONDS.convert(
            nDaysToKeepResults, TimeUnit.DAYS);

        ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                removeOldFiles();
            }
        }, 0, 1, TimeUnit.DAYS);
    }

    private void startJobOperation(File directory) {
        Lock lock = null;
        synchronized (synchronizers) {
            if (!synchronizers.containsKey(directory)) {
                synchronizers.put(directory, new Lock());
                return;
            }

            lock = synchronizers.get(directory);
        }

        lock.waitForUnlock();
    }

    private void endJobOperation(File directory) {
        synchronized (synchronizers) {
            Lock lock = synchronizers.get(directory);
            if (!lock.unlock()) {
                synchronizers.remove(directory);
            }
        }
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
            startJobOperation(idDirectory);
            try {
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
            } finally {
                endJobOperation(idDirectory);
            }
        }
        return null;
    }

    private Response getResultFile(
            File idDirectory, String filename, boolean download) {
        File resultFile = new File(idDirectory, filename);

        startJobOperation(idDirectory);

        try {
            File purgeFile = new File(
                resultsDirectory, PURGED_FILE + idDirectory.getName());
            if (purgeFile.exists()) {
                logger.debug(idDirectory + " was purged");
                return Response.status(Status.NOT_FOUND).entity(
                    "Results from job " + idDirectory.getName() +
                    " have been removed").build();
            }

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
        } finally {
            endJobOperation(idDirectory);
        }
    }

    @Override
    public Response getResultFile(
            String projectId, int id, String filename, boolean download) {
        logger.debug(
            "Retrieving " + filename + " from " + projectId + "/" + id);
        File projectDirectory = new File(resultsDirectory, projectId);
        File idDirectory = new File(projectDirectory, String.valueOf(id));
        return getResultFile(idDirectory, filename, download);
    }

    @Override
    public Response getResultFile(int id, String filename, boolean download) {
        logger.debug(
            "Retrieving " + filename + " from " + id);
        File idDirectory = new File(resultsDirectory, String.valueOf(id));
        return getResultFile(idDirectory, filename, download);
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
            startJobOperation(idDirectory);
            try {
                String uploadFilePath = filePath;
                if (uploadFilePath.endsWith("/")) {
                    uploadFilePath = uploadFilePath.substring(
                        0, uploadFilePath.length() - 1);
                }
                recursivelyUploadFiles(
                    idDirectory, fileManager, storageId, uploadFilePath);
                return Response.ok().build();
            } finally {
                endJobOperation(idDirectory);
            }
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

    private void emptyDirectory(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                emptyDirectory(file);
            }
            file.delete();
        }
    }

    private void removeOldFiles() {
        long startTime = System.currentTimeMillis();
        for (File projectDirectory : resultsDirectory.listFiles()) {
            if (projectDirectory.isDirectory()) {
                boolean allJobsRemoved = true;
                for (File jobDirectory : projectDirectory.listFiles()) {
                    logger.debug(
                        "Determining whether to remove " + jobDirectory +
                        " which is " +
                        (startTime - jobDirectory.lastModified()) +
                        "ms old of " + timeToKeepResults);
                    if (jobDirectory.isDirectory() &&
                            ((startTime - jobDirectory.lastModified()) >
                            timeToKeepResults)) {
                        logger.info(
                            "Removing results for job " +
                            jobDirectory.getName());
                        startJobOperation(jobDirectory);
                        emptyDirectory(jobDirectory);
                        endJobOperation(jobDirectory);

                        try {
                            File purgedFile = new File(
                                resultsDirectory,
                                PURGED_FILE + jobDirectory.getName());
                            PrintWriter purgedFileWriter =
                                new PrintWriter(purgedFile);
                            purgedFileWriter.println(
                                System.currentTimeMillis());
                            purgedFileWriter.close();
                        } catch (IOException e) {
                            logger.error("Error writing purge file", e);
                        }
                    } else {
                        allJobsRemoved = false;
                    }
                }
                if (allJobsRemoved) {
                    logger.info(
                        "No more outputs for project " +
                        projectDirectory.getName());
                    projectDirectory.delete();
                }
            }
        }
    }

}
