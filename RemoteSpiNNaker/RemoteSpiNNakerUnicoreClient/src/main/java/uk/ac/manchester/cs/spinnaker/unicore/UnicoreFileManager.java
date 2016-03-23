package uk.ac.manchester.cs.spinnaker.unicore;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.ws.rs.WebApplicationException;

import uk.ac.manchester.cs.spinnaker.rest.spring.SpringRestClientUtils;
import uk.ac.manchester.cs.spinnaker.unicore.rest.UnicoreFileClient;

/**
 * A manager for the UNICORE storage REST API
 */
public class UnicoreFileManager {

    private UnicoreFileClient fileClient = null;

    public UnicoreFileManager(URL url)
            throws KeyManagementException, NoSuchAlgorithmException {
        fileClient = SpringRestClientUtils.createOIDCClient(
            url, UnicoreFileClient.class);
    }

    /**
     * Upload a file
     * @param storageId The id of the storage on the server
     * @param filePath The path at which to store the file (directories are
     *                 automatically created)
     * @param input The input stream containing the file to upload
     * @throws IOException
     */
    public void uploadFile(String storageId, String filePath, InputStream input)
            throws IOException {
        try {
            fileClient.upload(storageId, filePath, input);
        } catch (WebApplicationException e) {
            throw new IOException(
                "Error uploading file to " + storageId + "/" + filePath, e);
        }
    }
}
