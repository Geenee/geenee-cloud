package it.geenee.cloud;

import io.netty.util.concurrent.Future;

import java.util.concurrent.ExecutionException;

public interface Cloud {

	// configuration

	/**
	 * Create a cloud configuration like this: Cloud.configure().region("eu-central-1").credentials(credentials).build();
	 */
	static ConfigBuilder configure() {
		return new ConfigBuilder();
	}

	class ConfigBuilder {
		public String region = null;
		public Object credentials =  null;
		public int timeout = 0;
		public int retryCount = 0;

		// storage specific
		public int partSize = 0;
		public int channelCount = 0;
		public String prefix = null;

		public ConfigBuilder region(String region) {
			this.region = region;
			return this;
		}

		public ConfigBuilder credentials(Credentials credentials) {
			this.credentials = credentials;
			return this;
		}

		public ConfigBuilder credentials(String accessKey, String secretAccessKey) {
			this.credentials = new Credentials(accessKey, secretAccessKey);
			return this;
		}

		public ConfigBuilder user(String user) {
			this.credentials = user != null ? user : "default";
			return this;
		}

		public ConfigBuilder timeout(int timeout) {
			this.timeout = timeout;
			return this;
		}

		public ConfigBuilder retryCount(int retryCount) {
			this.retryCount = retryCount;
			return this;
		}

		/**
		 * @param partSize size of parts for multipart file transfers
		 * @return configuration builder
		 */
		public ConfigBuilder partSize(int partSize) {
			this.partSize = partSize;
			return this;
		}

		/**
		 * @param channelCount maximum number of parallel channels for multipart file transfers
		 * @return configuration builder
		 */
		public ConfigBuilder channelCount(int channelCount) {
			this.channelCount = channelCount;
			return this;
		}

		/**
		 * @param prefix path prefix for storage operations
		 * @return configuration builder
		 */
		public ConfigBuilder prefix(String prefix) {
			this.prefix = prefix;
			return this;
		}
	}

	class Configuration {
		public final String region;
		public final Credentials credentials;
		public final int timeout;
		public final int retryCount;

		// storage specific
		public final int partSize;
		public final int channelCount;
		public final String prefix;

		public Configuration(String region, Credentials credentials, int timeout, int retryCount, int partSize, int channelCount, String prefix) {
			this.region = region;
			this.credentials = credentials;
			this.timeout = timeout;
			this.retryCount = retryCount;
			this.partSize = partSize;
			this.channelCount = channelCount;
			this.prefix = prefix;
		}

		public Configuration merge(Configuration configuration) {
			if (configuration == null)
				return this;
			return new Configuration(
					configuration.region != null ? configuration.region : this.region,
					configuration.credentials != null ? configuration.credentials : this.credentials,
					configuration.timeout > 0 ? configuration.timeout : this.timeout,
					configuration.retryCount > 0 ? configuration.retryCount : this.retryCount,
					configuration.partSize > 0 ? configuration.partSize : this.partSize,
					configuration.channelCount > 0 ? configuration.channelCount : this.channelCount,
					configuration.prefix != null ? configuration.prefix : this.prefix
			);
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
	Future<InstanceInfo> startGetInstance();
	default InstanceInfo getInstance() throws InterruptedException, ExecutionException {
		return startGetInstance().get();
	}

	// compute

	/**
	 * Get interface to compute sub-system
	 * @param configBuilder configuration that overrides the global configuration. may be null
	 * @return compute subsystem
	 */
	Compute getCompute(ConfigBuilder configBuilder);
	default Compute getCompute() {return getCompute(null);}

	// storage

	/**
	 * Get interface to storage sub-system
	 * @param configBuilder configuration that overrides the global configuration. may be null
	 * @return storage subsystem
	 */
	Storage getStorage(ConfigBuilder configBuilder);
	default Storage getStorage() {return getStorage(null);}


	// helpers

	static void appendKey(StringBuilder b, String key) {
		if (b.length() > 1)
			b.append(", ");
		b.append(key);
		b.append(": ");
	}
	static StringBuilder append(StringBuilder b, String key, boolean value) {
		appendKey(b, key);
		b.append(value);
		return b;
	}
	static StringBuilder append(StringBuilder b, String key, long value) {
		appendKey(b, key);
		b.append(value);
		return b;
	}
	static StringBuilder append(StringBuilder b, String key, String value) {
		appendKey(b, key);
		if (value != null)
			b.append('"');
		b.append(value);
		if (value != null)
			b.append('"');
		return b;
	}
	static StringBuilder append(StringBuilder b, String key, Object value) {
		appendKey(b, key);
		if (value == null)
			b.append((String)null);
		else
			b.append(value.toString());
		return b;
	}
}
