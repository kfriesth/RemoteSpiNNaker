package uk.ac.manchester.cs.spinnaker.remote.webutils;

import java.net.URL;

import javax.ws.rs.RedirectionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import uk.ac.manchester.cs.spinnaker.collab.model.CollabPermissions;
import uk.ac.manchester.cs.spinnaker.collab.rest.CollabRestService;
import uk.ac.manchester.cs.spinnaker.rest.spring.SpringRestClientUtils;

public class CollabSecurityService {

    private URL collabServiceUrl = null;

    private Log logger = LogFactory.getLog(getClass());

    public CollabSecurityService(URL collabServiceUrl) {
        this.collabServiceUrl = collabServiceUrl;
    }

    private CollabPermissions getPermissions(int id) {
        CollabRestService service = SpringRestClientUtils.createOIDCClient(
                collabServiceUrl, CollabRestService.class);
        try {
            return service.getCollabPermissions(id);
        } catch (RedirectionException e) {
            logger.error("Got a redirect to " + e.getLocation(), e);
            return null;
        } catch (Exception e) {
            logger.error(
                "Error getting collab permissions - assumed access denied", e);

            return null;
        }
    }

    public boolean canRead(int id) {
        CollabPermissions permissions = getPermissions(id);
        if (permissions == null) {
            return false;
        }
        return permissions.isView();
    }

    public boolean canUpdate(int id) {
        CollabPermissions permissions = getPermissions(id);
        if (permissions == null) {
            return false;
        }
        return permissions.isUpdate();
    }
}
