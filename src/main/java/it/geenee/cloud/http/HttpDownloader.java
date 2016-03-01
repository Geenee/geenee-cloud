package it.geenee.cloud.http;

import java.io.IOException;
import java.nio.channels.FileChannel;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;

/**
 * Generic HTTP multipart downloader that uses standard HEAD and GET requests
 */
public class HttpDownloader extends HttpTransfer {

	public HttpDownloader(HttpCloud cloud, Configuration configuration, FileChannel file, String host, String urlPath, String version) {
		super(cloud, configuration, file, host, urlPath);

		// how to do it with bootstrap
		/*
		Boostrap bootstrap = new Bootstrap();
		bootstrap
				.group(cloud.eventLoopGroup)
				.channel(NioSocketChannel.class)
				//.handler(new Initializer(cloud.sslCtx))
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.timeout * 1000);

		// connect to get file length via HEAD start (Handler.channelActive gets called if connection was successful)
		bootstrap.connect(host, PORT);
		*/

		final String urlPathAndVersion = cloud.addVersion(urlPath, version);

		// connect to host
		// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectHEAD.html
		connect(new RequestHandler() {
			@Override
			protected FullHttpRequest getRequest() throws Exception {
				return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, urlPathAndVersion);
			}

			@Override
			protected void success(FullHttpResponse response) throws Exception {
				headDone(response.headers());
			}
		});
	}

	// helpers

	void headDone(HttpHeaders headers) throws IOException {
		// head start is done

		// get hash of file in cloud storage
		this.hash = this.cloud.getHash(headers);

		// get current version of file in cloud storage. this way we stick to this version
		// even if a new version becomes the current version during download
		this.version = this.cloud.getVersion(headers);

		// get file length
		long fileLength = Long.parseLong(headers.get("Content-Length"));

		// resize local file
		this.file.truncate(fileLength);

		startTransfer(fileLength, null);
	}

	@Override
	protected void connect(Part part) {
		// build path and query, add version if there is one
		String urlPathAndVersion = this.cloud.addVersion(this.urlPath, this.version);

		// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectGET.html
		connect(new DownloadHandler(urlPathAndVersion, part) {
			@Override
			protected void success(Part part) {
				// set state of part to SUCCESS (downloaded part has no id)
				part.success(null);
			}
		});
	}

	@Override
	protected void completeTransfer() {
		setSuccess(null);
	}
}
