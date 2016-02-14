package it.geenee.cloud;

/**
 * Cloud configuration
 */
public class Configuration {
	public final String region;
	public final Credentials credentials;
	public final int partSize;
	public final int threadCount;
	public final int timeout;
	public final int retryCount;

	public Configuration(String region, Credentials credentials, int partSize, int threadCount, int timeout, int retryCount) {
		this.region = region;
		this.credentials = credentials;
		this.partSize = partSize;
		this.threadCount = threadCount;
		this.timeout = timeout;
		this.retryCount = retryCount;
	}

	public Configuration merge(Configuration configuration) {
		if (configuration == null)
			return this;
		return new Configuration(
				configuration.region != null ? configuration.region : this.region,
				configuration.credentials != null ? configuration.credentials : this.credentials,
				configuration.partSize > 0 ? configuration.partSize : this.partSize,
				configuration.threadCount > 0 ? configuration.threadCount : this.threadCount,
				configuration.timeout > 0 ? configuration.timeout : this.timeout,
				configuration.retryCount > 0 ? configuration.retryCount : this.retryCount
		);
	}


	// helpers

	static void appendKey(StringBuilder b, String key) {
		if (b.length() > 1)
			b.append(", ");
		b.append(key);
		b.append(": ");
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
	static StringBuilder append(StringBuilder b, String key, boolean value) {
		appendKey(b, key);
		b.append(value);
		return b;
	}

}
