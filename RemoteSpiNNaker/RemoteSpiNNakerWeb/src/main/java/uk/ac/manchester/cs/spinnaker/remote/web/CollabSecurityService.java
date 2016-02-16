package uk.ac.manchester.cs.spinnaker.remote.web;

import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import uk.ac.manchester.cs.spinnaker.collab.rest.CollabRestService;
import uk.ac.manchester.cs.spinnaker.rest.spring.SpringRestClientUtils;

public class CollabSecurityService {

    private URL collabServiceUrl = null;

    private Log logger = LogFactory.getLog(getClass());

    public CollabSecurityService(URL collabServiceUrl) {
        this.collabServiceUrl = collabServiceUrl;
    }

    public boolean canRead(int id) {

        CollabRestService service = SpringRestClientUtils.createOIDCClient(
            collabServiceUrl, CollabRestService.class);
        try {
            service.getCollabPermissions(id);
        } catch (Exception e) {
            logger.debug(
                "Error getting collab permissions - assumed access denied", e);

            return false;
        }
        return true;
    }

}
