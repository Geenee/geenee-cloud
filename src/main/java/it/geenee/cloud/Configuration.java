package it.geenee.cloud;

/**
 * Cloud configuration
 */
public class Configuration {
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
	static StringBuilder append(StringBuilder b, String key, long value) {
		appendKey(b, key);
		b.append(value);
		return b;
	}
	static StringBuilder append(StringBuilder b, String key, boolean value) {
		appendKey(b, key);
		b.append(value);
		return b;
	}
}
