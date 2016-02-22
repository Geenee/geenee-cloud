package it.geenee.cloud;

import io.netty.util.concurrent.Future;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface Compute {

	// instances

	interface Instances {
		/**
		 * Filter instances. Instances pass the filter if they have the given key set to one of the given values.
		 * The key is cloud provider specific, use one of the following methods to be provider independent.
		 * AWS: http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeInstances.html
		 * @param key
		 * @param values
		 * @return this object to apply more filters or get() the instances
		 */
		Instances filter(String key, String... values);

		Instances filterZone(String... zones);
		Instances filterTag(String key, String... values);
		Instances filterTagKey(String... keys);
		Instances filterTagValue(String... values);

		Future<List<InstanceInfo>> request();
		default List<InstanceInfo> get() throws InterruptedException, ExecutionException {
			return request().get();
		}
	}

	/**
	 * Returns an object for retrieving info about instances. You can add filters to list only specific intances.
	 * For example use like this: compute.instances().filterZone("eu-central-1a").filterTag("Role", "master").get();
	 * @return instance lister object
	 */
	Instances instances();

	// tags

	/**
	 * Get the tags associated with the given resource (instance id, ami id etc.)
	 * @return future for tag key/value pairs
	 */
	Future<Map<String, String>> startGetTags(String resourceId);
	default Map<String, String> getTags(String resourceId) throws InterruptedException, ExecutionException {
		return startGetTags(resourceId).get();
	}
}
