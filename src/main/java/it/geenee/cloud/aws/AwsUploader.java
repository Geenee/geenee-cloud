package it.geenee.cloud.aws;

import java.io.*;
import java.nio.channels.FileChannel;

import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpTransfer;

/**
 * AWS PUT Object
 */
public class AwsUploader extends HttpTransfer {

	static HttpTransfer create(HttpCloud cloud, Configuration configuration, FileChannel file, String host, String urlPath) {
		try {
			long fileSize = file.size();
			if (fileSize <= configuration.partSize)
				return new AwsUploader(cloud, configuration, file, fileSize, host, urlPath);
			else
				return new AwsMultipartUploader(cloud, configuration, file, host, urlPath);
		} catch (IOException e) {
			return new AwsUploader(cloud, configuration, file, host, urlPath, e);
		}
	}

	private AwsUploader(HttpCloud cloud, Configuration configuration, FileChannel file, long fileSize, String host, String urlPath) {
		super(cloud, configuration, file, host, urlPath);
		startTransfer(fileSize, null);
	}

	private AwsUploader(HttpCloud cloud, Configuration configuration, FileChannel file, String host, String urlPath, Throwable cause) {
		super(cloud, configuration, file, host, urlPath);
		setFailed(cause);
	}

	// helpers

	@Override
	protected void connect(Part part) {
		// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectPUT.html
		connect(new UploadHandler(urlPath, part) {
			@Override
			protected void success(Part part, HttpHeaders headers) {
				// set state of part to SUCCESS (uploaded part has no id)
				part.success(null);

				// upload done (there is only one part)
				uploadDone(headers);
			}
		});
	}

	void uploadDone(HttpHeaders headers) {
		// get hash of file in S3
		this.hash = this.cloud.getHash(headers);

		// get current version of file in S3
		this.version = this.cloud.getVersion(headers);
	}

	@Override
	protected void completeTransfer() {
		setSuccess(null);
	}
}
