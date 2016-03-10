package it.geenee.cloud.http;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Date;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;

/**
 * Generic HTTP multipart downloader that uses standard HEAD and GET requests
 */
public class HttpDownloader extends HttpTransfer {

	public HttpDownloader(HttpCloud cloud, Configuration configuration, FileChannel file, String host, final String remotePath, final String requestedVersion) {
		super(cloud, configuration, file, host, HttpCloud.encodePath('/' + configuration.prefix + remotePath));

		final String urlPathAndVersion = cloud.addVersion(this.urlPath, requestedVersion);

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

		// connect to host
		// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectHEAD.html
		connect(new RequestHandler() {
			@Override
			protected FullHttpRequest getRequest() throws Exception {
				return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, urlPathAndVersion);
			}

			@Override
			protected void success(FullHttpResponse response) throws Exception {
				HttpHeaders headers = response.headers();
				HttpDownloader parent = HttpDownloader.this;

				String hash = parent.cloud.getHash(headers);
				long size = Long.parseLong(headers.get("Content-Length"));
				long timestamp = HttpHeaders.getDateHeader(response, "Last-Modified").getTime(); // https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1
				String version = parent.cloud.getVersion(headers);

				synchronized (parent) {
					parent.fileInfo = new FileInfo(remotePath, hash, size, timestamp, version, requestedVersion == null);
				}

				// resize local file
				parent.file.truncate(size);

				startTransfer(size, null);
			}
		});
	}

	// helpers

	@Override
	protected void connect(Part part) {
		// build path and query, add version that we obtained in HEAD request so that we stick to one version during multipart download even if a new version
		// becomes available
		String urlPathAndVersion = this.cloud.addVersion(this.urlPath, this.fileInfo.version);

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
		// download completed successfully
		setSuccess(this.fileInfo);
	}
}
