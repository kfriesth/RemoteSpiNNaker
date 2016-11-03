package uk.ac.manchester.cs.spinnaker.remote.web;

import static uk.ac.manchester.cs.spinnaker.rest.spring.SpringRestClientUtils.createOIDCClient;

import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import uk.ac.manchester.cs.spinnaker.collab.rest.CollabRestService;

public class CollabSecurityService {
	private Log logger = LogFactory.getLog(getClass());
	private final URL collabServiceUrl;

	public CollabSecurityService(URL collabServiceUrl) {
		this.collabServiceUrl = collabServiceUrl;
	}

	public boolean canRead(int id) {
		// Do not factor out; depends on thread context
		CollabRestService service = createOIDCClient(collabServiceUrl,
				CollabRestService.class);
		try {
			service.getCollabPermissions(id);
			return true;
		} catch (Exception e) {
			logger.debug(
					"Error getting collab permissions - assumed access denied",
					e);
			return false;
		}
	}
}