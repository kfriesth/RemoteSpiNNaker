package uk.ac.manchester.cs.spinnaker.collab;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;

import org.jboss.resteasy.client.core.BaseClientResponse;

import uk.ac.manchester.cs.spinnaker.collab.model.DocEntity;
import uk.ac.manchester.cs.spinnaker.collab.model.DocEntityReturn;
import uk.ac.manchester.cs.spinnaker.collab.rest.DocumentRestService;
import uk.ac.manchester.cs.spinnaker.rest.spring.SpringRestClientUtils;

public class DocumentClient {

    private DocumentRestService client = null;

    public DocumentClient(URL url) {
        client = SpringRestClientUtils.createOIDCClient(
            url, DocumentRestService.class);
    }

    public String getProjectUuidByCollabId(String collabId) {
        List<DocEntityReturn> projects = client.getAllProjects(
            "managed_by_collab=" + collabId);
        return projects.get(0).getUuid();
    }

    public String getFileUuidByPath(String path) {
        return client.getEntityByPath(path).getUuid();
    }

    public String getPathById(String id) {
        DocEntityReturn entity = client.getEntityByUUID(id);
        StringBuilder builder = new StringBuilder();
        if (entity.getParent() != null) {
            builder.append(entity.getParent());
        }
        builder.append('/');
        builder.append(entity.getName());
        return builder.toString();
    }

    public String createFolder(String parentUuid, String name) {
        DocEntity folder = new DocEntity(parentUuid, name);
        DocEntityReturn returnValue = client.createFolder(folder);
        return returnValue.getUuid();
    }

    public void uploadFile(String parentUuid, String name, InputStream input) {
        DocEntity file = new DocEntity(parentUuid, name);
        DocEntityReturn response = client.createFile(file);
        client.uploadFile(response.getUuid(), input);
    }

    public void downloadFile(String path, OutputStream output)
            throws IOException {
        String uuid = getFileUuidByPath(path);
        BaseClientResponse<?> response =
            (BaseClientResponse<?>) client.downloadFile(uuid);
        byte[] buffer = new byte[8192];
        InputStream input = response.getStreamFactory().getInputStream();
        int bytesRead = input.read(buffer);
        while (bytesRead != -1) {
            output.write(buffer, 0, bytesRead);
            bytesRead = input.read(buffer);
        }
        input.close();
    }
}