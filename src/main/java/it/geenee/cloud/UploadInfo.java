package it.geenee.cloud;

import java.util.Date;

/**
 * Information about an upload
 */
public class UploadInfo {
	// path of the file
	public final String path;

	// id of the upload
	public final String uploadId;

	// timestamp of the upload, the number of milliseconds since January 1, 1970, 00:00:00 GMT
	public final long timestamp;


	public UploadInfo(String path, String uploadId, long timestamp) {
		this.path = path;
		this.uploadId = uploadId;
		this.timestamp = timestamp;
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('{');
		Cloud.append(b, "path", this.path);
		Cloud.append(b, "uploadId", this.uploadId);
		Cloud.append(b, "timestamp", FileInfo.DATE_FORMAT.format(new Date(this.timestamp)));
		b.append('}');
		return b.toString();
	}
}
