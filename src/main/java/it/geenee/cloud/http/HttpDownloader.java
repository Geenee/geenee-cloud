package it.geenee.cloud.http;

import java.io.IOException;
import java.nio.channels.FileChannel;

import io.netty.buffer.ByteBuf;
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
			String uri = remotePath;
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

	class PartHandler extends HttpTransfer.Handler {
		Part part;
		int position;

		PartHandler(Part part) {
			this.part = part;
		}

		@Override
		public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
			// pipeline gets built

			// part is now initializing
			this.part.setState(Part.State.INITIATING);
			stateChange();

			super.handlerAdded(ctx);
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// connection is established

			// build uri, addPart version if there is one
			String uri = remotePath;
			if (version != null)
				uri += '?' + cloud.getVersionParameter() + '=' + version;

			// generate HTTP request
			FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
			HttpHeaders headers = request.headers();
			headers.set(HttpHeaders.Names.HOST, host);
			long begin = this.part.offset;
			long end = begin + this.part.length;
			headers.set(HttpHeaders.Names.RANGE, "bytes=" + begin + '-' + (end - 1));
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
					// success: set state of part to PROGRESS
					this.part.setState(Transfer.Part.State.PROGRESS);
					this.position = 0;

					// notify state change to wainting threads
					stateChange();
				}
			} else if (msg instanceof HttpContent) {
				HttpContent content = (HttpContent) msg;

				if (this.responseCode / 100 == 2) {
					// write content to file
					ByteBuf buf = content.content();
					this.position += file.write(buf.nioBuffer(), this.part.offset + this.position);

					//System.err.print(content.content().toString(CharsetUtil.UTF_8));
					//System.err.flush();

					if (content instanceof LastHttpContent) {
						//System.err.println("} END OF CONTENT");

						// set state of part to SUCCESS
						this.part.setState(Transfer.Part.State.DONE);

						// notify state change to wainting threads
						stateChange();

						// part done, start next part or complete download if no more parts
						partDone(this.part);

						ctx.close();
					}
				} else {
					// http error (e.g. 400)
					//System.err.print(content.content().toString(HttpCloud.UTF_8));
					if (content instanceof LastHttpContent) {
						ctx.close();

						// check if transfer has failed
						if (!isRetryCode())
							cancel();
					}
				}
			}
		}

		@Override
		protected boolean hasFailed() {
			// if state of part is not done (e.g. on read timeout), download of part has failed
			return this.part.getState() != Part.State.DONE;
		}

		@Override
		public boolean retry(int maxRetryCount) {
			return this.part.retry(maxRetryCount);
		}
	}

	public HttpDownloader(HttpCloud cloud, FileChannel file, String host, String remotePath, String version, Configuration configuration) {
		super(cloud, file, host, remotePath, configuration);

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
		connect(new PartHandler(part));
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

	void partDone(Part part) {
		// a part is done
		startPart();
	}

	@Override
	protected void completeTransfer() {
		setState(State.SUCCESS);
		stateChange();
	}
}
