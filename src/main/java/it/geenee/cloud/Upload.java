package it.geenee.cloud;

/**
 * Instance description
 */
public class Upload {
	// path of the file
	public final String path;

	// id of the upload
	public final String uploadId;


	public Upload(String path, String uploadId) {
		this.path = path;
		this.uploadId = uploadId;
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('{');
		Configuration.append(b, "path", this.path);
		Configuration.append(b, "uploadId", this.uploadId);
		b.append('}');
		return b.toString();
	}
}
