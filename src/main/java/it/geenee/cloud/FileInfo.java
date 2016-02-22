package it.geenee.cloud;

/**
 * Information about a file
 */
public class FileInfo {
	// path of the file
	public final String path;

	// hash of the file, null if this is a delete marker
	public final String hash;

	// size of file
	public final long size;

	// timestamp in iso format and utc time zone
	public final String timestamp;

	// version of the file, null if no versioning or unknown
	public final String version;

	// true if this is the latest version of the file
	public final boolean latest;


	public FileInfo(String path, String hash, long size, String timestamp, String version, boolean latest) {
		this.path = path;
		this.hash = hash;
		this.size = size;
		this.timestamp = timestamp;
		this.version = version;
		this.latest = latest;
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('{');
		Configuration.append(b, "path", this.path);
		Configuration.append(b, "hash", this.hash);
		Configuration.append(b, "size", this.size);
		Configuration.append(b, "timestamp", this.timestamp);
		Configuration.append(b, "version", this.version);
		Configuration.append(b, "latest", this.latest);
		b.append('}');
		return b.toString();
	}
}
