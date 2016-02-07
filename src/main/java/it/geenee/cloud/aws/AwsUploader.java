package it.geenee.cloud.aws;

import java.io.*;
import java.nio.channels.FileChannel;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;

import it.geenee.cloud.*;
import it.geenee.cloud.ChunkedContent;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpTransfer;

/**
 * AWS PUT Object
 */
public class AwsUploader extends HttpTransfer {

	// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectPUT.html
	class UploadHandler extends HttpTransfer.Handler {
		// there is only one part
		Part part;

		UploadHandler(Part part) {
			this.part = part;
		}

		@Override
		public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
			// pipeline gets built

			// part is now initializing
			this.part.setState(Part.State.INITIATING);

			// add handler for file upload after http handler
			ctx.pipeline().addAfter("http", "writer", new ChunkedWriteHandler());

			super.handlerAdded(ctx);
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// connection is established: send http request to server

			// build uri for upload part
			String uri = remotePath;

			// build http request (without content as we send it on receiving continue 100 status code)
			HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, uri);
			HttpHeaders headers = request.headers();
			headers.set(HttpHeaders.Names.HOST, host);
			headers.set(HttpHeaders.Names.EXPECT, HttpHeaders.Values.CONTINUE);
			cloud.extendRequest(request, file, this.part.offset, this.part.length, configuration);

			// send the http request
			ctx.writeAndFlush(request);

			super.channelActive(ctx);
		}

		@Override
		public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
			if (msg instanceof HttpResponse) {
				HttpResponse response = (HttpResponse) msg;

				// get http response code
				this.responseCode = response.getStatus().code();
				if (this.responseCode == 100) {
					// continue: now send the part of the file
					ctx.writeAndFlush(new ChunkedContent(file, this.part.offset, this.part.length, 8192),
							ctx.newProgressivePromise());

					// set state of part to PROGRESS
					this.part.setState(Part.State.PROGRESS);
				} else if (this.responseCode / 100 == 2) {
					// success

					// set state of part to SUCCESS
					this.part.setState(Part.State.DONE);

					// part done, start next part or complete upload if no more parts
					uploadDone(this.part, response.headers());
				}
			} else if (msg instanceof HttpContent) {
				HttpContent content = (HttpContent) msg;

				if (this.responseCode == 100) {
					// continue: keep connection open
				} else if (this.responseCode / 100 == 2) {
					// http request succeeded
					if (content instanceof LastHttpContent) {
						ctx.close();
					}
				} else {
					// http error (e.g. 400)
					//System.err.println(content.content().toString(HttpCloud.UTF_8));
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
			// if state of part is not done (e.g. on read timeout), upload of part has failed
			return this.part.getState() != Part.State.DONE;
		}

		@Override
		public boolean retry(int maxRetryCount) {
			return this.part.retry(maxRetryCount);
		}
	}

	public AwsUploader(HttpCloud cloud, FileChannel file, String host, String remotePath, Configuration configuration) throws IOException {
		super(cloud, file, host, remotePath, configuration);

		startTransfer(file.size(), null);
	}

	// helpers

	@Override
	protected void connect(Part part) {
		connect(new UploadHandler(part));
	}

	void uploadDone(Part part, HttpHeaders headers) {
		// get hash of file in S3
		this.hash = this.cloud.getHash(headers);

		// get current version of file in S3
		this.version = this.cloud.getVersion(headers);

		setState(State.SUCCESS);
	}

	@Override
	protected void completeTransfer() {
		// not used
	}
}
