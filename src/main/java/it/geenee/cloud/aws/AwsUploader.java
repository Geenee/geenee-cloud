package it.geenee.cloud.aws;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.Date;

import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpTransfer;

/**
 * AWS PUT Object
 */
public class AwsUploader extends HttpTransfer {
	String remotePath;
	long size;

	static HttpTransfer create(HttpCloud cloud, Cloud.Configuration configuration, FileChannel file, String host, String remotePath) {
		String urlPath = HttpCloud.encodePath('/' + configuration.prefix + remotePath);
		try {
			long size = file.size();
			if (size <= configuration.partSize)
				return new AwsUploader(cloud, configuration, file, host, urlPath, remotePath, size);
			else
				return new AwsMultipartUploader(cloud, configuration, file, host, urlPath, remotePath, size);
		} catch (IOException e) {
			// return failed transfer
			return new AwsUploader(cloud, configuration, file, host, urlPath, e);
		}
	}

	private AwsUploader(HttpCloud cloud, Cloud.Configuration configuration, FileChannel file, String host, String urlPath, String remotePath, long size) {
		super(cloud, configuration, file, host, urlPath);
		this.remotePath = remotePath;
		this.size = size;

		// start upload with one part
		startTransfer(size, null);
	}

	private AwsUploader(HttpCloud cloud, Cloud.Configuration configuration, FileChannel file, String host, String urlPath, Throwable cause) {
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

				AwsUploader parent = AwsUploader.this;

				// FIXME timestamp
				String hash = parent.cloud.getHash(headers);
				long timestamp = 0;
				String version = parent.cloud.getVersion(headers);

				synchronized (parent) {
					parent.fileInfo = new FileInfo(parent.remotePath, hash, parent.size, timestamp, version, true);
				}
			}
		});
	}

	@Override
	protected void completeTransfer() {
		// upload completed successfully
		setSuccess(this.fileInfo);
	}
}
