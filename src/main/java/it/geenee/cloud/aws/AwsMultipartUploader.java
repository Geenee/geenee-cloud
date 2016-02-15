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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * AWS mulitpart uploader
 */
public class AwsMultipartUploader extends HttpTransfer {

	// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadInitiate.html
	class InitiateHandler extends HttpTransfer.RequestHandler {
		@Override
		protected FullHttpRequest getRequest() throws Exception {
			return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, remotePath + "?uploads");
		}

		@Override
		protected void success(byte[] content) throws Exception {
			// parse xml
			JAXBContext jc = JAXBContext.newInstance(InitiateMultipartUploadResult.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			InitiateMultipartUploadResult result = (InitiateMultipartUploadResult) unmarshaller.unmarshal(new ByteArrayInputStream(content));

			// initate done, start upload
			initiateDone(result);
		}
	}

	// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadUploadPart.html
	class PartHandler extends Handler {
		Part part;

		PartHandler(Part part) {
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
			String uri = remotePath + "?partNumber=" + (this.part.index + 1) + "&uploadId=" + id;
			//System.out.println("PartHandler.channelActive uri: " + uri);

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
				//System.out.println("PartHandler.channelRead0 code: " + this.responseCode);

				if (this.responseCode == 100) {
					// continue: now send the part of the file
					ctx.writeAndFlush(new ChunkedContent(file, this.part.offset, this.part.length, 8192),
							ctx.newProgressivePromise());

					// set state of part to PROGRESS
					this.part.setState(Part.State.PROGRESS);
				} else if (this.responseCode / 100 == 2) {
					// success: get ETag of part
					this.part.id = response.headers().get("ETag");

					// set state of part to SUCCESS
					this.part.setState(Part.State.DONE);

					// part done, start next part or complete upload if no more parts
					partDone(this.part);
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
					//System.err.println("part " + content.content().toString(HttpCloud.UTF_8));
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

	// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadComplete.html
	class CompleteHandler extends HttpTransfer.RequestHandler {
		CompleteMultipartUpload completeMultipartUpload;

		CompleteHandler(CompleteMultipartUpload completeMultipartUpload) {
			this.completeMultipartUpload = completeMultipartUpload;
		}

		@Override
		protected FullHttpRequest getRequest() throws Exception {
			FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, remotePath + "?uploadId=" + id);

			// set CompleteMultipartUpload as content
			JAXBContext jc = JAXBContext.newInstance(CompleteMultipartUpload.class);
			Marshaller marshaller = jc.createMarshaller();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			marshaller.marshal(completeMultipartUpload, os);
			request.content().writeBytes(os.toByteArray());

			return request;
		}

		@Override
		protected void success(byte[] content) throws Exception {
			// parse xml
			JAXBContext jc = JAXBContext.newInstance(CompleteMultipartUploadResult.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			CompleteMultipartUploadResult result = (CompleteMultipartUploadResult) unmarshaller.unmarshal(new ByteArrayInputStream(content));

			// complete done, transfer was successful
			completeDone(result);
		}
	}

	public AwsMultipartUploader(HttpCloud cloud, Configuration configuration, FileChannel file, String host, String remotePath) {
		super(cloud, configuration, file, host, remotePath);

		// connect to host
		connect(new InitiateHandler());
	}

	// helpers

	@Override
	protected void connect(Part part) {
		connect(new PartHandler(part));
	}

	void initiateDone(InitiateMultipartUploadResult result) throws IOException {
		System.out.println("initiateDone uploadId " + result.uploadId);

		startTransfer(this.file.size(), result.uploadId);
	}

	void partDone(Part part) {
		// a part is done
		startPart();
	}

	@Override
	protected void completeTransfer() {
		// complete multipart upload
		CompleteMultipartUpload completeMultipartUpload = new CompleteMultipartUpload();
		for (Part part : this.parts) {
			completeMultipartUpload.addPart(part.index + 1, part.id);
		}
		connect(new CompleteHandler(completeMultipartUpload));
	}

	void completeDone(CompleteMultipartUploadResult result) {
		// get hash of file in S3 from ETag
		this.hash = AwsCloud.getHash(result.eTag);

		setState(State.SUCCESS);
	}
}
