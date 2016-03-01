package it.geenee.cloud.http;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;


/**
 * Base class for HTTP based upload and download
 */
public abstract class HttpTransfer extends HttpFuture<Void> implements Transfer  {

	protected final FileChannel file;
	protected final String urlPath;

	// the HTTP ETag of the file in the cloud storage
	protected String hash = null;

	// version of the file in the cloud storage
	protected String version = null;

	// id of transfer, only used for some transfer types, e.g. multipart upload
	protected String id = null;

	protected List<Part> parts;

	protected class Part implements Transfer.Part {
		public final int index;
		public final long offset;
		public final int length;

		// state of part
		private State state = State.QUEUED;
		private int retryCount = 0;

		// id of part, only used for some transfer types, e.g. multipart upload
		public String id = null;

		public Part(int index, long offset, int length) {
			this.index = index;
			this.offset = offset;
			this.length = length;
		}

		@Override
		public int getIndex() {
			return this.index;
		}

		@Override
		public long getOffset() {
			return this.offset;
		}

		@Override
		public int getLength() {
			return this.length;
		}

		@Override
		public synchronized State getState() {
			return this.state;
		}

		@Override
		public synchronized int getRetryCount() {
			return this.retryCount;
		}

		@Override
		public synchronized String getId() {
			return this.id;
		}

		// helpers

		public synchronized boolean start() {
			if (this.state == State.QUEUED) {
				this.state = State.INITIATING;
				stateChange();
				return true;
			}
			return false;
		}

		public synchronized void setState(State state) {
			this.state = state;
			stateChange();
		}

		public synchronized void success(String id) {
			this.id = id;
			this.state = State.SUCCESS;
			stateChange();
		}

		public synchronized boolean retry(int maxRetryCount) {
			if (++this.retryCount >= maxRetryCount) {
				this.state = State.FAILED;

				// whole transfer fails and will notify state change after setting its state to FAILED
				return true;
			}
			this.state = State.RETRY;
			stateChange();
			return false;
		}
	}

	public abstract class UploadHandler extends HttpTransfer.Handler<HttpObject> {
		final String urlPath;
		final Part part;

		static final int CHUNK_SIZE = 8192;
		int responseCode;
		boolean uploading = false;
		long position;

		public UploadHandler(String urlPath, Part part) {
			this.urlPath = urlPath;
			this.part = part;
		}

		@Override
		public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
			// pipeline gets built

			// part is now initializing
			this.part.setState(Part.State.INITIATING);

			super.handlerAdded(ctx);
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// connection is established: send http request to server

			// build http request (without content as we send it on receiving continue 100 status code)
			HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, this.urlPath);
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
					// continue: now start send the part of the file (continues in channelWritabilityChanged())
					this.uploading = true;
					this.position = 0;
					upload(ctx);

					// set state of part to PROGRESS
					this.part.setState(Part.State.PROGRESS);
				} else if (this.responseCode / 100 == 2) {
					// success
					success(this.part, response.headers());
					this.success = true;

					// part done, start next part or complete upload if no more parts
					startPart();
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

						// transfer has failed, maybe retry is possible
						setFailed(isRetryCode(this.responseCode), new HttpException(this.responseCode));
					}
				}
			}
		}

		protected abstract void success(Part part, HttpHeaders headers);

		@Override
		public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
			if (this.uploading)
				upload(ctx);
			ctx.fireChannelWritabilityChanged();
		}

		void upload(ChannelHandlerContext ctx) throws IOException {
			Channel channel = ctx.channel();
			while (channel.isWritable()) {
				if (this.position >= this.part.length) {
					// send last chunk for this input
					ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
					this.uploading = false;
					break;
				} else {
					int bufferSize = (int) Math.min(CHUNK_SIZE, this.part.length - position);
					ByteBuf buffer = ctx.alloc().heapBuffer(bufferSize);

					boolean release = true;
					try {
						buffer.writerIndex(bufferSize);
						//System.out.println("read o: " + (this.offset + this.position) + " s: " + bufferSize);
						file.read(buffer.nioBuffer(), this.part.offset + this.position);
						this.position += bufferSize;
						release = false;
						ctx.writeAndFlush(new DefaultHttpContent(buffer));
					} finally {
						if (release) {
							buffer.release();
						}
					}
				}
			}
		}

		@Override
		public boolean retry(int maxRetryCount) {
			return this.part.retry(maxRetryCount);
		}
	}

	abstract class DownloadHandler extends HttpTransfer.Handler<HttpObject> {
		final String urlPath;
		final Part part;

		int responseCode;
		int position;

		DownloadHandler(String urlPath, Part part) {
			this.urlPath = urlPath;
			this.part = part;
		}

		@Override
		public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
			// pipeline gets built

			// part is now initializing
			this.part.setState(Part.State.INITIATING);

			super.handlerAdded(ctx);
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// connection is established

			// generate HTTP request
			FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, this.urlPath);
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
				}
			} else if (msg instanceof HttpContent) {
				HttpContent content = (HttpContent) msg;

				if (this.responseCode / 100 == 2) {
					// write content to file
					ByteBuf buf = content.content();
					this.position += file.write(buf.nioBuffer(), this.part.offset + this.position);

					if (content instanceof LastHttpContent) {
						// success
						success(this.part);
						this.success = true;

						// part done, start next part or complete upload if no more parts
						startPart();

						ctx.close();
					}
				} else {
					// http error (e.g. 400)
					//System.err.println(content.content().toString(HttpCloud.UTF_8));
					if (content instanceof LastHttpContent) {
						ctx.close();

						// transfer has failed, maybe retry is possible
						setFailed(isRetryCode(this.responseCode), new HttpException(this.responseCode));
					}
				}
			}
		}

		protected abstract void success(Part part);

		@Override
		public boolean retry(int maxRetryCount) {
			return this.part.retry(maxRetryCount);
		}
	}

	/**
	 * Constructor
	 * @param cloud the HttpCloud instance
	 * @param configuration configuration
	 * @param file file channel of the file to upload or download. Only read and write with explicit position are used
	 * @param host host to connect to
	 * @param urlPath remote path of file
	 */
	public HttpTransfer(HttpCloud cloud, Configuration configuration, FileChannel file, String host, String urlPath) {
		super(cloud, configuration, host, true);

		this.file = file;
		this.urlPath = urlPath;
	}

	@Override
	public synchronized void waitForStateChange() throws InterruptedException {
		wait();
	}

	@Override
	public String getUrl() {
		return "https://" + this.host + this.urlPath;
	}

	@Override
	public synchronized int getPartCount() {
		return this.parts == null ? 0 : this.parts.size();
	}

	@Override
	public Part getPart(int index) {
		return this.parts.get(index);
	}

	@Override
	public String getHash() {
		return this.hash;
	}

	@Override
	public String getVersion() {
		return this.version;
	}

	@Override
	public String getId() {
		return this.id;
	}

 	// helpers

	protected void startTransfer(long fileLength, String id) {
		// create parts
		long partSize = this.configuration.partSize;
		int partCount = (int) ((fileLength + partSize - 1) / partSize);
		List<Part> parts = new ArrayList<>((int) partCount);
		for (int partIndex = 0; partIndex < partCount; ++partIndex) {
			long begin = partIndex * partSize;
			long end = begin + partSize;
			if (end > fileLength) {
				// last part might be smaller
				end = fileLength;
			}
			parts.add(new Part(partIndex, begin, (int) (end - begin)));
		}
		this.id = id;

		synchronized (this) {
			this.parts = parts;
		}

		setState(State.PROGRESS);

		// start first parts
		int startCount = Math.min(partCount, this.configuration.channelCount);
		for (int i = 0; i < startCount; ++i) {
			Part part = parts.get(i);
			part.start();
			connect(part);
		}
		stateChange();
	}

	protected void startPart() {
		// try to start a part
		for (Part part : this.parts) {
			if (part.start()) {
				stateChange();
				connect(part);
				return;
			}
		}

		// all parts are already started: check if parts still in progress
		for (Part part : this.parts) {
			if (part.getState() != Part.State.SUCCESS)
				return;
		}

		// all parts are done: complete transfer
		completeTransfer();
	}

	protected abstract void connect(Part part);

	protected abstract void completeTransfer();
}
