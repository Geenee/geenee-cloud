package it.geenee.cloud;

import io.netty.util.concurrent.Future;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface Compute {

	/**
	 * Get the tags associated with the given resource (instance id, ami id etc.)
	 * @return future for tag key/value pairs
	 */
	Future<Map<String, String>> requestTags(String resourceId);
	default Map<String, String> getTags(String resourceId) throws InterruptedException, ExecutionException {
		return requestTags(resourceId).get();
	}


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

		Future<List<Instance>> request();
		default List<Instance> get() throws InterruptedException, ExecutionException {
			return request().get();
		}
	}

	Instances instances();
}
