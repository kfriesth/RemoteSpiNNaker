package uk.ac.manchester.cs.spinnaker.remote.web;

import static java.util.Objects.requireNonNull;
import static uk.ac.manchester.cs.spinnaker.rest.utils.SpringRestClientUtils.createOIDCClient;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import uk.ac.manchester.cs.spinnaker.model.CollabPermissions;
import uk.ac.manchester.cs.spinnaker.rest.CollabRestService;

public class CollabSecurityService {
	private Logger logger = LoggerFactory.getLogger(getClass());
	@Value("${collab.service.uri}")
	private URL collabServiceUrl;

	private CollabRestService getServiceInstance() {
		// Do not factor out; depends on thread context
		return createOIDCClient(collabServiceUrl, CollabRestService.class);
	}

	private CollabPermissions getPermissions(int id) {
		return requireNonNull(getServiceInstance().getCollabPermissions(id));
	}

	public boolean canRead(int id) {
		try {
			getPermissions(id);
			return true;
		} catch (Exception e) {
			logger.debug("Error getting collab permissions, "
					+ "assumed access denied", e);
			return false;
		}
	}

	public boolean canUpdate(int id) {
		try {
			return getPermissions(id).isUpdate();
		} catch (Exception e) {
			logger.debug("Error getting collab permissions, "
					+ "assumed access denied", e);
			return false;
		}
	}

	public boolean canDelete(int id) {
		try {
			return getPermissions(id).isDelete();
		} catch (Exception e) {
			logger.debug("Error getting collab permissions, "
					+ "assumed access denied", e);
			return false;
		}
	}
}
