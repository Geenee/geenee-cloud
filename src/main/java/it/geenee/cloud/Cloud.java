package it.geenee.cloud;

import io.netty.util.concurrent.Future;

import java.util.concurrent.ExecutionException;

public interface Cloud {

	// configuration

	/**
	 * Create a cloud configuration like this: Cloud.configure().region("eu-central-1").credentials(credentials).build();
	 */
	static Configure configure() {
		return new Configure();
	}

	class Configure {
		String region = null;
		Credentials credentials =  null;
		int timeout = 0;
		int retryCount = 0;

		// storage specific
		int partSize = 0;
		int channelCount = 0;
		String prefix = null;

		public Configure region(String region) {
			this.region = region;
			return this;
		}

		public Configure credentials(Credentials credentials) {
			this.credentials = credentials;
			return this;
		}

		public Configure timeout(int timeout) {
			this.timeout = timeout;
			return this;
		}

		public Configure retryCount(int retryCount) {
			this.retryCount = retryCount;
			return this;
		}

		/**
		 * @param partSize size of parts for multipart file transfers
		 * @return configuration builder
		 */
		public Configure partSize(int partSize) {
			this.partSize = partSize;
			return this;
		}

		/**
		 * @param channelCount maximum number of parallel channels for multipart file transfers
		 * @return configuration builder
		 */
		public Configure channelCount(int channelCount) {
			this.channelCount = channelCount;
			return this;
		}

		/**
		 * @param prefix path prefix for storage operations
		 * @return configuration builder
		 */
		public Configure prefix(String prefix) {
			this.prefix = prefix;
			return this;
		}

		public Configuration build() {
			return new Configuration(
					this.region,
					this.credentials,
					this.timeout,
					this.retryCount,
					this.partSize,
					this.channelCount,
					this.prefix);
		}
	}

	// general

	/**
	 * Get credentials for the given user
	 * @param user name of user
	 * @return credentials or null if not found
	 */
	Credentials getCredentials(String user);

	// this instance

	/**
	 * Get instance data of the instance we are currently running on
	 */
	Future<InstanceInfo> requestInstance();
	default InstanceInfo getInstance() throws InterruptedException, ExecutionException {
		return requestInstance().get();
	}

	// compute

	/**
	 * Get interface to compute sub-system
	 * @param configuration configuration that overrides the global configuration. may be null
	 * @return compute subsystem
	 */
	Compute getCompute(Configuration configuration);
	default Compute getCompute() {return getCompute(null);}

	// storage

	/**
	 * Get interface to storage sub-system
	 * @param configuration configuration that overrides the global configuration. may be null
	 * @return storage subsystem
	 */
	Storage getStorage(Configuration configuration);
	default Storage getStorage() {return getStorage(null);}
}
