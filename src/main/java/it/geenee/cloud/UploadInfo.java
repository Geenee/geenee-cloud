package it.geenee.cloud;

/**
 * Information about an upload
 */
public class UploadInfo {
	// path of the file
	public final String path;

	// id of the upload
	public final String uploadId;

	// timestamp in iso format and utc time zone
	public final String timestamp;


	public UploadInfo(String path, String uploadId, String timestamp) {
		this.path = path;
		this.uploadId = uploadId;
		this.timestamp = timestamp;
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('{');
		Configuration.append(b, "path", this.path);
		Configuration.append(b, "uploadId", this.uploadId);
		Configuration.append(b, "timestamp", this.timestamp);
		b.append('}');
		return b.toString();
	}
}
