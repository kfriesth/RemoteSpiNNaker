package uk.ac.manchester.cs.spinnaker.output;

import static java.lang.System.currentTimeMillis;
import static java.nio.file.Files.move;
import static java.nio.file.Files.probeContentType;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.ac.manchester.cs.spinnaker.rest.utils.RestClientUtils.createBearerClient;

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

import javax.annotation.PostConstruct;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import uk.ac.manchester.cs.spinnaker.job.nmpi.DataItem;
import uk.ac.manchester.cs.spinnaker.rest.OutputManager;
import uk.ac.manchester.cs.spinnaker.rest.UnicoreFileClient;

//TODO needs security; Role = OutputHandler
@Component
public class OutputManagerImpl implements OutputManager {
    private static final String PURGED_FILE = ".purged_";

    @Value("${results.directory}")
    private File resultsDirectory;
	@Value("${baseserver.url}${cxf.path}${cxf.rest.path}/")
	private URL baseServerUrl;
    private long timeToKeepResults;
    private final Map<File, JobLock.Token> synchronizers = new HashMap<>();
    private Logger logger = getLogger(getClass());

    private class JobLock implements AutoCloseable {
    	private class Token {
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
    	
    	private File dir;
    	JobLock(File dir) {
    		this.dir = dir;

    		Token lock;
    		synchronized (synchronizers) {
				if (!synchronizers.containsKey(dir)){
					// Constructed pre-locked
					synchronizers.put(dir, new Token());
					return;
				}
				lock = synchronizers.get(dir);
    		}

    		lock.waitForUnlock();
    	}
    	
    	@Override
    	public void close() {
    		synchronized (synchronizers) {
    			Token lock = synchronizers.get(dir);
    			if (!lock.unlock())
    				synchronizers.remove(dir);
    		}
    	}
    }

    @Value("${results.purge.days}")
    void setPurgeTimeout(long nDaysToKeepResults) {
    	timeToKeepResults = MILLISECONDS.convert(nDaysToKeepResults, DAYS);
    }

    @PostConstruct
    void initPurgeScheduler() {
		ScheduledExecutorService scheduler = newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				removeOldFiles();
			}
		}, 0, 1, DAYS);
    }

    private File getProjectDirectory(String projectId) {
		if (isBlank(projectId) || projectId.endsWith("/"))
			throw new IllegalArgumentException("bad projectId");
		String name = new File(projectId).getName();
		if (isBlank(name) || name.startsWith("."))
			throw new IllegalArgumentException("bad projectId");
		return new File(resultsDirectory, name);
	}

    @Override
	public List<DataItem> addOutputs(String projectId, int id,
			File baseDirectory, Collection<File> outputs) throws IOException {
		if (outputs == null)
			return null;

		String pId = new File(projectId).getName();
		int pathStart = baseDirectory.getAbsolutePath().length();
		File projectDirectory = getProjectDirectory(projectId);
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
    	// TODO projectId and id? What's going on? 
		logger.debug("Retrieving " + filename + " from " + projectId + "/" + id);
		File projectDirectory = getProjectDirectory(projectId);
		File idDirectory = new File(projectDirectory, String.valueOf(id));
		return getResultFile(idDirectory, filename, download);
	}

    @Override
    public Response getResultFile(int id, String filename, boolean download) {
    	// TODO projectId and NO id? What's going on? 
        logger.debug(
            "Retrieving " + filename + " from " + id);
        File idDirectory = getProjectDirectory(String.valueOf(id));
        return getResultFile(idDirectory, filename, download);
    }

	private void recursivelyUploadFiles(File directory,
			UnicoreFileClient fileManager, String storageId, String filePath)
			throws IOException {
		File[] files = directory.listFiles();
		if (files == null)
			return;
		for (File file : files) {
			if (file.getName().equals(".") || file.getName().equals("..")
					|| file.getName().trim().isEmpty())
				continue;
			String uploadFileName = filePath + "/" + file.getName();
			if (file.isDirectory()) {
				recursivelyUploadFiles(file, fileManager, storageId,
						uploadFileName);
				continue;
			}
			if (!file.isFile())
				continue;
			try (FileInputStream input = new FileInputStream(file)) {
				fileManager.upload(storageId, uploadFileName, input);
			} catch (WebApplicationException e) {
				throw new IOException("Error uploading file to " + storageId
						+ "/" + uploadFileName, e);
			} catch (FileNotFoundException e) {
				// Ignore files which vanish.
			}
		}
	}

    @Override
	public Response uploadResultsToHPCServer(String projectId, int id,
			String serverUrl, String storageId, String filePath, String userId,
			String token) {
    	// TODO projectId and id? What's going on? 
		File idDirectory = new File(getProjectDirectory(projectId),
				String.valueOf(id));
    	if (!idDirectory.canRead()) {
    		logger.debug(idDirectory + " was not found");
    		return Response.status(NOT_FOUND).build();
    	}
    	
        try {
			UnicoreFileClient fileClient = createBearerClient(
					new URL(serverUrl), token, UnicoreFileClient.class);
			try (JobLock op = new JobLock(idDirectory)) {
				recursivelyUploadFiles(idDirectory, fileClient, storageId,
						filePath.replaceAll("/+$", ""));
			}
		} catch (MalformedURLException e) {
			logger.error("bad user-supplied URL", e);
			return Response.status(BAD_REQUEST)
					.entity("The URL specified was malformed").build();
		} catch (Throwable e) {
			logger.error("failure in upload", e);
			return Response.status(INTERNAL_SERVER_ERROR)
					.entity("General error reading or uploading a file")
					.build();
		}

        return Response.ok().entity("ok").build();
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
