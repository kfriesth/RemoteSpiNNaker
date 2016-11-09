package uk.ac.manchester.cs.spinnaker.output.impl;

import static java.lang.System.currentTimeMillis;
import static java.nio.file.Files.move;
import static java.nio.file.Files.probeContentType;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;

import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.output.OutputManager;
import uk.ac.manchester.cs.spinnaker.unicore.UnicoreFileManager;

public class OutputManagerImpl implements OutputManager {
    private static final String PURGED_FILE = ".purged_";

    private File resultsDirectory;
    private URL baseServerUrl;
    private long timeToKeepResults;
    private final Map<File, LockToken> synchronizers = new HashMap<>();
    private Logger logger = getLogger(getClass());

    private static class LockToken {
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
    
    private class JobLock implements AutoCloseable {
    	private File dir;
    	JobLock(File dir) {
    		this.dir = dir;

    		LockToken lock = null;
    		synchronized (synchronizers) {
				if (!synchronizers.containsKey(dir))
					synchronizers.put(dir, new LockToken());
				else
					lock = synchronizers.get(dir);
    		}

			if (lock != null)
				lock.waitForUnlock();
    	}
    	
    	@Override
    	public void close() {
    		synchronized (synchronizers) {
    			LockToken lock = synchronizers.get(dir);
    			if (!lock.unlock())
    				synchronizers.remove(dir);
    		}
    	}
    }

	public OutputManagerImpl(URL baseServerUrl, File resultsDirectory,
			long nDaysToKeepResults) {
		this.baseServerUrl = baseServerUrl;
		this.resultsDirectory = resultsDirectory;
		this.timeToKeepResults = MILLISECONDS.convert(nDaysToKeepResults, DAYS);

		ScheduledExecutorService scheduler = newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				removeOldFiles();
			}
		}, 0, 1, DAYS);
	}

    @Override
	public List<DataItem> addOutputs(String projectId, int id,
			File baseDirectory, Collection<File> outputs) throws IOException {
		if (outputs == null)
			return null;

		String pId = new File(projectId).getName();
		int pathStart = baseDirectory.getAbsolutePath().length();
		File projectDirectory = new File(resultsDirectory, pId);
		File idDirectory = new File(projectDirectory, String.valueOf(id));

		try (JobLock op = new JobLock(idDirectory)) {
			List<DataItem> outputData = new ArrayList<>();
			for (File output : outputs) {
				if (!output.getAbsolutePath().startsWith(
						baseDirectory.getAbsolutePath()))
					throw new IOException("Output file " + output
							+ " is outside base directory " + baseDirectory);

				String outputPath = output.getAbsolutePath()
						.substring(pathStart).replace('\\', '/');
				if (outputPath.startsWith("/"))
					outputPath = outputPath.substring(1);

				File newOutput = new File(idDirectory, outputPath);
				newOutput.getParentFile().mkdirs();
				move(output.toPath(), newOutput.toPath());
				URL outputUrl = new URL(baseServerUrl, "output/" + pId + "/"
						+ id + "/" + outputPath);
				outputData.add(new DataItem(outputUrl.toExternalForm()));
				logger.debug("New output " + newOutput + " mapped to "
						+ outputUrl);
			}

			return outputData;
		}
    }

    private Response getResultFile(
            File idDirectory, String filename, boolean download) {
        File resultFile = new File(idDirectory, filename);
        File purgeFile = getPurgeFile(idDirectory);

        try (JobLock op = new JobLock(idDirectory)) {
            if (purgeFile.exists()) {
                logger.debug(idDirectory + " was purged");
				return Response
						.status(NOT_FOUND)
						.entity("Results from job " + idDirectory.getName()
								+ " have been removed").build();
            }

            if (!resultFile.canRead()) {
                logger.debug(resultFile + " was not found");
                return Response.status(NOT_FOUND).build();
            }

			try {
				if (!download) {
					String contentType = probeContentType(resultFile.toPath());
					if (contentType != null) {
						logger.debug("File has content type " + contentType);
						return Response.ok(resultFile, contentType).build();
					}
				}
			} catch (IOException e) {
				logger.debug("Content type of " + resultFile
						+ " could not be determined", e);
			}

			return Response
					.ok((Object) resultFile)
					.header("Content-Disposition",
							"attachment; filename=" + filename).build();
        }
    }

	private File getPurgeFile(File directory) {
		return new File(resultsDirectory, PURGED_FILE + directory.getName());
	}

	@Override
	public Response getResultFile(String projectId, int id, String filename,
			boolean download) {
		logger.debug("Retrieving " + filename + " from " + projectId + "/" + id);
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

	private void recursivelyUploadFiles(File directory,
			UnicoreFileManager fileManager, String storageId, String filePath)
			throws IOException {
		for (File file : directory.listFiles()) {
			String uploadFileName = filePath + "/" + file.getName();
			if (file.isDirectory())
				recursivelyUploadFiles(file, fileManager, storageId,
						uploadFileName);
			else
				try (FileInputStream input = new FileInputStream(file)) {
					fileManager.uploadFile(storageId, uploadFileName, input);
				} catch (FileNotFoundException e) {
					// Ignore files which vanish.
				}
		}
	}

    @Override
    public Response uploadResultsToHPCServer(
            String projectId, int id, String serverUrl, String storageId,
            String filePath, String userId, String token) {
        try {
			UnicoreFileManager fileManager = new UnicoreFileManager(new URL(
					serverUrl), userId, token);
            File projectDirectory = new File(resultsDirectory, projectId);
            File idDirectory = new File(projectDirectory, String.valueOf(id));
            if (!idDirectory.canRead()) {
                logger.debug(idDirectory + " was not found");
                return Response.status(NOT_FOUND).build();
            }
			try (JobLock op = new JobLock(idDirectory)) {
				String uploadFilePath = filePath;
				if (uploadFilePath.endsWith("/"))
					uploadFilePath = uploadFilePath.substring(0,
							uploadFilePath.length() - 1);
				recursivelyUploadFiles(idDirectory, fileManager, storageId,
						uploadFilePath);
				return Response.ok().build();
            }
		} catch (MalformedURLException e) {
			logger.error("bad user-supplied URL", e);
			return Response.status(BAD_REQUEST)
					.entity("The URL specified was malformed").build();
		} catch (IOException e) {
			logger.error("failure in upload", e);
			return Response.status(INTERNAL_SERVER_ERROR)
					.entity("General error reading or uploading a file")
					.build();
		} catch (Throwable e) {
			logger.error("general failure in upload", e);
			return Response.status(INTERNAL_SERVER_ERROR).build();
		}
	}

    private void removeDirectory(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory())
                removeDirectory(file);
            else
                file.delete();
        }
        directory.delete();
    }

	private void removeOldFiles() {
		long startTime = currentTimeMillis();
		for (File projectDirectory : resultsDirectory.listFiles())
			if (projectDirectory.isDirectory()
					&& removeOldProjectDirectoryContents(startTime,
							projectDirectory)) {
				logger.info("No more outputs for project "
						+ projectDirectory.getName());
				projectDirectory.delete();
			}
	}

	private boolean removeOldProjectDirectoryContents(long startTime,
			File projectDirectory) {
		boolean allJobsRemoved = true;
		for (File jobDirectory : projectDirectory.listFiles()) {
			logger.debug("Determining whether to remove " + jobDirectory
					+ " which is " + (startTime - jobDirectory.lastModified())
					+ "ms old of " + timeToKeepResults);
			if (jobDirectory.isDirectory()
					&& (startTime - jobDirectory.lastModified() > timeToKeepResults)) {
				logger.info("Removing results for job "
						+ jobDirectory.getName());
				try (JobLock op = new JobLock(jobDirectory)) {
					removeDirectory(jobDirectory);
				}

				try (PrintWriter purgedFileWriter = new PrintWriter(
						getPurgeFile(jobDirectory))) {
					purgedFileWriter.println(currentTimeMillis());
				} catch (IOException e) {
					logger.error("Error writing purge file", e);
				}
			} else
				allJobsRemoved = false;
		}
		return allJobsRemoved;
	}
}
