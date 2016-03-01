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

	// timestamp
	public final Date timestamp;

	// version of the file, null if no versioning or unknown
	public final String version;

	// true if this is the latest version of the file
	public final boolean latest;


	static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
	static {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
	}


	public FileInfo(String path, String hash, long size, Date timestamp, String version, boolean latest) {
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
		Configuration.append(b, "path", this.path);
		Configuration.append(b, "hash", this.hash);
		Configuration.append(b, "size", this.size);
		Configuration.append(b, "timestamp", DATE_FORMAT.format(this.timestamp));
		Configuration.append(b, "version", this.version);
		Configuration.append(b, "latest", this.latest);
		b.append('}');
		return b.toString();
	}
}
