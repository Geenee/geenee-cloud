package it.geenee.cloud;

import io.netty.util.concurrent.Future;

import java.util.Map;

public interface Compute {

	/**
	 * Get the tags associated with the given resource (instance id, ami id etc.)
	 * @return future for tag key/value pairs
	 */
	Future<Map<String, String>> getTags(String resourceId);

}
