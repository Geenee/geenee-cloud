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

	// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectHEAD.html
	class HeadHandler extends HttpTransfer.Handler {
		String version;
		int retryCount = 0;

		HeadHandler(String version) {
			this.version = version;
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// connection is established

			// build uri, addPart version if there is one
			String uri = urlPath;
			if (this.version != null)
				uri += '?' + cloud.getVersionParameter() + '=' + this.version;

			// generate HTTP request
			FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, uri);
			HttpHeaders headers = request.headers();
			headers.set(HttpHeaders.Names.HOST, host);
			cloud.extendRequest(request, configuration);

			// send the HTTP request
			ctx.writeAndFlush(request);

			super.channelActive(ctx);
		}

		@Override
		public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
			if (msg instanceof HttpResponse) {
				HttpResponse response = (HttpResponse) msg;

				// get http response code
				this.responseCode = response.getStatus().code();

				if (this.responseCode / 100 == 2) {
					// success: head request done, start download of parts
					headDone(response.headers());
				}
			} else if (msg instanceof HttpContent) {
				HttpContent content = (HttpContent) msg;

				if (this.responseCode / 100 == 2) {
					// http request succeeded
					if (content instanceof LastHttpContent) {
						ctx.close();
					}
				} else {
					// http error (e.g. 400)
					System.err.println(content.content().toString(HttpCloud.UTF_8));
					if (content instanceof LastHttpContent) {
						ctx.close();

						// chck if transfer has failed
						if (!isRetryCode())
							cancel();
					}
				}
			}
		}

		@Override
		protected boolean hasFailed() {
			// if state of transfer is still initiating (e.g. on read timeout), head request has failed
			return getState() == Transfer.State.INITIATING;
		}

		@Override
		public boolean retry(int maxRetryCount) {
			return ++this.retryCount >= maxRetryCount;
		}
	}

	public HttpDownloader(HttpCloud cloud, Configuration configuration, FileChannel file, String host, String remotePath, String version) {
		super(cloud, configuration, file, host, remotePath);

		// how to do it with bootstrap
		/*
		Boostrap bootstrap = new Bootstrap();
		bootstrap
				.group(cloud.eventLoopGroup)
				.channel(NioSocketChannel.class)
				//.handler(new Initializer(cloud.sslCtx))
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.timeout * 1000);

		// connect to get file length via HEAD request (Handler.channelActive gets called if connection was successful)
		bootstrap.connect(host, PORT);
		*/

		// connect to host
		connect(new HeadHandler(version));
	}

	// helpers

	@Override
	protected void connect(Part part) {
		// build path and query, add version if there is one
		String urlPath = this.urlPath;
		if (this.version != null)
			urlPath += '?' + this.cloud.getVersionParameter() + '=' + this.version;

		// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectGET.html
		connect(new DownloadHandler(urlPath, part) {
			@Override
			protected void success(Part part) {
				// set state of part to SUCCESS (downloaded part has no id)
				part.success(null);
			}
		});
	}

	void headDone(HttpHeaders headers) throws IOException {
		// head request is done

		// get hash of file in cloud storage
		this.hash = this.cloud.getHash(headers);

		// get current version of file in cloud storage. this way we stick to this version
		// even if a new version becomes the current version during download
		this.version = this.cloud.getVersion(headers);

		// get file length
		String contentLength = headers.get("Content-Length");
		long fileLength = Long.parseLong(contentLength);

		// resize local file
		this.file.truncate(fileLength);

		startTransfer(fileLength, null);
	}

	@Override
	protected void completeTransfer() {
		setSuccess(null);
	}
}
