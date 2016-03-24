package it.geenee.cloud;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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

	// timestamp of file, the number of milliseconds since January 1, 1970, 00:00:00 GMT
	public final long timestamp;

	// version of the file, null if no versioning or unknown
	public final String version;

	// true if this is the latest version of the file, false if newer versions exist or unknown if newer versions exist
	public final boolean latest;


	static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
	static {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
	}


	public FileInfo(String path, String hash, long size, long timestamp, String version, boolean latest) {
		this.path = path;
		this.hash = hash;
		this.size = size;
		this.timestamp = timestamp;
		this.version = version;
		this.latest = latest;
	}

	public String getTimestampIso() {
		return DATE_FORMAT.format(this.timestamp);
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('{');
		Cloud.append(b, "path", this.path);
		Cloud.append(b, "hash", this.hash);
		Cloud.append(b, "size", this.size);
		Cloud.append(b, "timestamp", DATE_FORMAT.format(new Date(this.timestamp)));
		Cloud.append(b, "version", this.version);
		Cloud.append(b, "latest", this.latest);
		b.append('}');
		return b.toString();
	}
}
