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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pac4j.oidc.profile.OidcProfile;
import org.pac4j.springframework.security.authentication.ClientAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nimbusds.oauth2.sdk.token.BearerAccessToken;

import uk.ac.manchester.cs.spinnaker.collab.DocumentClient;
import uk.ac.manchester.cs.spinnaker.output.OutputManager;
import uk.ac.manchester.cs.spinnaker.unicore.UnicoreFileManager;

public class OutputManagerImpl implements OutputManager {

    private static final String PURGED_FILE = ".purged_";

    private File resultsDirectory = null;

    private URL baseServerUrl = null;

    private URL collabStorageUrl = null;

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
            URL baseServerUrl, URL collabStorageUrl, File resultsDirectory,
            long nDaysToKeepResults) {
        this.baseServerUrl = baseServerUrl;
        this.collabStorageUrl = collabStorageUrl;
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
    public List<String> addOutputs(
            String projectId, int id, File baseDirectory, List<File> outputs)
            throws IOException {
        if (outputs != null) {
            String pId = new File(projectId).getName();
            int pathStart = baseDirectory.getAbsolutePath().length();
            File projectDirectory = new File(resultsDirectory, pId);
            File idDirectory = new File(projectDirectory, String.valueOf(id));
            startJobOperation(idDirectory);
            try {
                List<String> outputData = new ArrayList<String>();
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
                    Files.move(output.toPath(), newOutput.toPath());
                    URL outputUrl = new URL(
                        baseServerUrl,
                        "output/" + pId + "/" + id + "/" + outputPath);
                    outputData.add(outputUrl.toExternalForm());
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

    private void recursivelyUploadFilesToHPC(
            File directory, UnicoreFileManager fileManager, String storageId,
            String filePath, String relativePath, Set<String> files)
                throws IOException {
        for (File file : directory.listFiles()) {
            String uploadFileName = filePath + "/" + file.getName();
            String relativeFileName = relativePath + "/" + file.getName();
            if (file.isDirectory()) {
                recursivelyUploadFilesToHPC(
                    file, fileManager, storageId, uploadFileName,
                    relativeFileName, files);
            } else if ((files == null) || files.isEmpty() ||
                    files.contains(relativeFileName)) {
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
            String filePath, Set<String> files) {
        try {
            URL url = new URL(serverUrl);
            UnicoreFileManager fileManager = new UnicoreFileManager(url);
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
                recursivelyUploadFilesToHPC(
                    idDirectory, fileManager, storageId, uploadFilePath,
                    "", files);
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

    private class ParentState {

        private ParentState parent;

        private String uuid;

        private String name;

        public ParentState(
                ParentState parent, String uuid, String name) {
            this.parent = parent;
            this.uuid = uuid;
            this.name = name;
        }
    }

    private void recursivelyCreateFolders(
            ParentState folder, DocumentClient fileManager) {
        if (folder.parent != null) {
            if (folder.parent.uuid == null) {
                recursivelyCreateFolders(folder.parent, fileManager);
            }
        }
        if (folder.uuid == null) {
            logger.debug("Creating folder " + folder.name);
            String uuid = fileManager.createFolder(
                folder.parent.uuid, folder.name);
            folder.uuid = uuid;
        }
    }

    private void recursivelyUploadFilesToCollabStorage(
            File directory, DocumentClient fileManager, ParentState parent,
            String relativePath, Set<String> files) throws IOException {

        for (File file : directory.listFiles()) {
            String relativeFileName = relativePath + "/" + file.getName();
            if (file.isDirectory()) {
                recursivelyUploadFilesToCollabStorage(
                    file, fileManager,
                    new ParentState(parent, null, file.getName()),
                    relativeFileName, files);
            } else if ((files == null) || files.isEmpty() ||
                    files.contains(relativeFileName)) {

                recursivelyCreateFolders(parent, fileManager);

                String contentType = Files.probeContentType(file.toPath());

                FileInputStream input = new FileInputStream(file);
                try {
                    logger.debug(
                        "Uploading " + file.getName() + " with type " +
                        contentType);
                    fileManager.uploadFile(
                        parent.uuid, file.getName(), input, contentType);
                } finally {
                    input.close();
                }
            }
        }
    }

    @Override
    public Response uploadResultsToCollabStorage(
            String projectId, int id, String filePath, Set<String> files) {

        try {
            DocumentClient fileManager = new DocumentClient(collabStorageUrl);
            File projectDirectory = new File(resultsDirectory, projectId);
            File idDirectory = new File(projectDirectory, String.valueOf(id));
            if (!idDirectory.canRead()) {
                logger.debug(idDirectory + " was not found");
                return Response.status(Status.NOT_FOUND).build();
            }

            // Get the collab storage folder
            String uuid = fileManager.getProjectUuidByCollabId(projectId);

            // Create the parent hierarchy, but don't create the folders yet
            ParentState parent = new ParentState(
                null, uuid, projectId);
            for (String pathItem : filePath.split("/")) {
                if (!pathItem.equals("")) {
                    parent = new ParentState(parent, null, pathItem);
                }
            }

            startJobOperation(idDirectory);
            try {
                recursivelyUploadFilesToCollabStorage(
                    idDirectory, fileManager, parent, "", files);
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
            e.printStackTrace();
            logger.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void removeDirectory(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                removeDirectory(file);
            } else {
                file.delete();
            }
        }
        directory.delete();
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
                        removeDirectory(jobDirectory);
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

    public static void main(String[] args) throws Exception {
        try {
            URL baseServerUrl = new URL("http://127.0.0.1:9090");
            URL collabStorageUrl = new URL(
                "https://services.humanbrainproject.eu/");
            File resultsDirectory = new File(
                "C:\\Users\\zzalsar4\\Documents\\APT\\remoteSpinnakerResults");

            String token = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImJicC1vaWRjIn0.eyJleHAiOjE0NTg5NzcyNzYsImF1ZCI6WyI5OGY2NzBiNi0xNzc1LTQxMjEtYWUyMi0yZDk3YWRiZWU5YTYiXSwiaXNzIjoiaHR0cHM6XC9cL3NlcnZpY2VzLmh1bWFuYnJhaW5wcm9qZWN0LmV1XC9vaWRjXC8iLCJqdGkiOiI2N2E5MDMxZS00MDI0LTQ2NTgtOGU3Mi1kNjgyM2QxZGVjMTgiLCJoYnBfc2lkIjoicnNoYzUwdXZqOGZ4MW9oNnRmdm12ZG9yZSIsImlhdCI6MTQ1ODgwNDQ3Nn0.pDuyIVumYElFFKhL0SYG8zQ3ifRlC59lukLV_OXvVHyiTME-30S0-G6-eV0f0qhJt4IejOegE5sgHlyOl1YkSGlnp9_Pfrb3wmADSTlhKk-LMa3E_-eCCinxVtWJuuyD1oh-1YUnClleddGxTrdKaUQjw5n3eWVr31LuOT0zNi4";
            OutputManagerImpl outputManager = new OutputManagerImpl(
                baseServerUrl, collabStorageUrl, resultsDirectory, 365);

            SecurityContextHolder.getContext().setAuthentication(
                    new ClientAuthenticationToken(
                        null, null, new OidcProfile(new BearerAccessToken(token)),
                        null));
            outputManager.uploadResultsToCollabStorage(
                "508", 3, "testPath", new HashSet<String>());
        } finally {
            System.exit(0);
        }
    }

}
