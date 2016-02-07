package it.geenee.cloud.http;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.*;

import it.geenee.cloud.*;


/**
 * Base class for HTTP based upload and download
 */
public abstract class HttpTransfer extends HttpFuture<Void> implements Transfer  {

	//public static final int PORT = 443;

	// needed to sign the requests using a provider specific algorithm
	//protected final HttpCloud cloud;

	// configuration
	//protected final Configuration configuration;

	protected final FileChannel file;
	//protected final String host;
	protected final String remotePath;

	// state of transfer
	//private State state = State.INITIATING;

	// the HTTP ETag of the file in the cloud storage
	protected String hash = null;

	// version of the file in the cloud storage
	protected String version = null;

	// id of transfer, only used for some transfer types, e.g. multipart upload
	protected String id = null;

	//Set<ChannelFuture> connectFutures = new HashSet<>();
	//Set<ChannelFuture> closeFutures = new HashSet<>();
	// set of active channels
	//Set<Channel> channels = new HashSet<>();

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
		public String getId() {
			return this.id;
		}

		// helpers

		public synchronized boolean start() {
			if (this.state == State.QUEUED) {
				this.state = State.INITIATING;
				return true;
			}
			return false;
		}

		public synchronized void setState(State state) {
			this.state = state;
			stateChange();
		}

		public synchronized boolean retry(int maxRetryCount) {
			if (++this.retryCount >= maxRetryCount) {
				this.state = State.FAILED;
				return true;
			}
			this.state = State.RETRY;
			stateChange();
			return false;
		}
	}
/*
	protected abstract class Handler extends SimpleChannelInboundHandler<HttpObject> {
		// http response code
		protected int responseCode = 0;

		@Override
		public boolean isSharable() {
			// enable adding/removing multiple times, but the handler is used by one pipeline at a time
			return true;
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			// all exceptions (e.g. IOExcepton from local file) lead to immediate failure of transfer
			cancel();

			// close connection, channelInactive will be called next
			ctx.close();
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
			if (event instanceof IdleStateEvent) {
				IdleStateEvent e = (IdleStateEvent) event;
				if (e.state() == IdleState.ALL_IDLE) {
					// connection timed out: try again until retryCount reaches maximum
					ctx.close();
				}
			}
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			if (hasFailed()) {
				// remove this handler from pipeline so that it can be reused
				ctx.pipeline().remove(this);

				// fail, may try again
				fail(this);
			}

			super.channelInactive(ctx);

			// remove channel from set of active channels
			synchronized (channels) {
				channels.remove(ctx.channel());
			}
		}

		/ **
		 * Returns true if handler has failed
		 * @return true if failed
		 * /
		protected abstract boolean hasFailed();

		/ **
		 * Increments the retry count and returns true if failed because maximum retry count has been reached
		 * @return true if failed because maximum retry count has been reached
		 * /
		public abstract boolean retry(int maxRetryCount);

		/ **
		 * @return true if the status code indicates failure but a retry makes sense
		 * /
		public boolean isRetryCode() {
			return this.responseCode == 400
					|| this.responseCode == 408
					|| this.responseCode == 429;
		}
	}
*/


	public HttpTransfer(HttpCloud cloud, FileChannel file, String host, String remotePath, Configuration configuration) {
		super(cloud, host, configuration, true);
		//this.cloud = cloud;
		//this.configuration = configuration;

		this.file = file;
		//this.host = host;
		this.remotePath = remotePath;
	}

	//@Override
	//public synchronized State getState() {
//		return this.state;
//	}

	@Override
	public synchronized void waitForStateChange() throws InterruptedException {
		wait();
	}

	@Override
	public String getUrl() {
		return this.host + this.remotePath;
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
 /*
	@Override
	public void cancel() {
		setState(State.FAILED);
		stateChange();

		// close all channels
		synchronized (this.channels) {
			for (Channel channel : this.channels) {
				channel.close();
			}
		}
	}
*/
	// helpers

	//protected synchronized void setState(State state) {
	//	this.state = state;
	//}
/*
	protected void connect(final Handler handler) {
		int timeout = this.configuration.timeout;

		// create channel
		Channel channel = new NioSocketChannel();

		// configure channel
		ChannelConfig config = channel.config();
		config.setOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout * 1000);

		// copied from netty Bootstrap

		// register channel
		final ChannelFuture registerFuture = this.cloud.eventLoopGroup.register(channel);
		if (registerFuture.cause() != null) {
			// registering failed
			if (channel.isRegistered())
				channel.close();
			else
				channel.unsafe().closeForcibly();

			// part failed while registering channel
			fail(handler);
			return;
		}

		// build channel pipeline
		final ChannelPipeline pipeline = channel.pipeline();
		{
			// https
			if (this.cloud.sslCtx != null)
				pipeline.addLast("ssl", this.cloud.sslCtx.newHandler(channel.alloc()));

			// timeout handler (throws ReadTimeoutException which appears in exceptionCaught() of Handler)
			//pipeline.addLast("timeout", new ReadTimeoutHandler(timeout));
			pipeline.addLast("timeout", new IdleStateHandler(0, 0, timeout));

			// http codec
			pipeline.addLast("http", new HttpClientCodec());

			// gzip decompressor
			pipeline.addLast("decompressor", new HttpContentDecompressor());

			// our handler for HTTP messages
			pipeline.addLast("transfer", handler);
		}

		// add channel to set of active channels
		synchronized (this.channels) {
			this.channels.add(channel);
		}

		// connect
		SocketAddress localAddress = null;
		SocketAddress remoteAddress = new InetSocketAddress(this.host, PORT);
		final ChannelPromise promise = channel.newPromise();
		if (registerFuture.isDone()) {
			// register channel already successful: connect now
			doConnect0(registerFuture, channel, remoteAddress, localAddress, promise);
		} else {
			// connect if register channel completes
			registerFuture.addListener((f) -> doConnect0(registerFuture, channel, remoteAddress, localAddress, promise));
		}

		// add listener that does retry on failure of connect
		promise.addListener((future) -> {
			// check if part failed while connect
			if (future.cause() != null) {
				pipeline.remove(handler);
				fail(handler);
			}
		});
	}

	// copied from netty Bootstrap
	static void doConnect0(final ChannelFuture regFuture, final Channel channel, final SocketAddress remoteAddress, final SocketAddress localAddress,
			final ChannelPromise promise) {

		// This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
		// the pipeline in its channelRegistered() implementation.
		channel.eventLoop().execute(() -> {
			if (regFuture.isSuccess()) {
				if (localAddress == null) {
					channel.connect(remoteAddress, promise);
				} else {
					channel.connect(remoteAddress, localAddress, promise);
				}
				promise.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
			} else {
				//! difference to netty: close channel if registration was unsuccessful
				channel.close();
				promise.setFailure(regFuture.cause());
			}
		});
	}

	void fail(Handler handler) {
		// check if transfer is already failed
		if (getState() == State.FAILED)
			return;

		// check if number of retries exceeded
		if (handler.retry(this.configuration.retryCount)) {
			// transfer has failed
			setState(State.FAILED);
			stateChange();
			return;
		}
		stateChange();

		// retry after a delay
		int delay = this.configuration.timeout;
		this.cloud.timer.newTimeout((timeout) -> connect(handler), delay, TimeUnit.SECONDS);
	}
*/
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
		int startCount = Math.min(partCount, this.configuration.threadCount);
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
			if (part.getState() != Part.State.DONE)
				return;
		}

		// all parts are done
		setState(State.COMPLETING);
		stateChange();

		// complete transfer
		completeTransfer();
	}

	protected abstract void connect(Part part);

	protected abstract void completeTransfer();



/*
	protected synchronized void stateChange() {
		notifyAll();
	}


*/
}
