package it.geenee.cloud.http;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import it.geenee.cloud.*;


/**
 * Base class for asynchronous HTTP requests that includes a base class for a ChannelInboundHandler
 */
public abstract class HttpFuture<V> implements Future<V> {

	public static final int HTTP_PORT = 80;
	public static final int HTTPS_PORT = 443;

	// cloud and configuration
	protected final HttpCloud cloud; // has cloud provider specific extendRequest() funciton
	protected final Configuration configuration;

	// host to connect to
	protected final String host;

	// http or https
	protected final boolean https;

	// state of query
	private Transfer.State state = Transfer.State.INITIATING;

	// set of active channels
	Set<Channel> channels = new HashSet<>();


	// result value
	V value;

	// cause of failure
	Throwable cause;
	Throwable notedCause;

	// future listeners
	List<GenericFutureListener<HttpFuture<V>>> listeners = new ArrayList<>();


	/**
	 * Base class for all http handlers that has retry logic already built in
	 */
	protected abstract class Handler<I> extends SimpleChannelInboundHandler<I> {
		protected boolean success = false;

		@Override
		public boolean isSharable() {
			// enable adding/removing multiple times, but the handler is used by one pipeline at a time
			return true;
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			// all exceptions (e.g. IOExcepton from local file) lead to immediate failure of transfer
			setFailed(cause);

			// setFailed also closes all connections
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
			if (event instanceof IdleStateEvent) {
				IdleStateEvent e = (IdleStateEvent) event;
				if (e.state() == IdleState.ALL_IDLE) {
					// connection timed out: try again until retryCount reaches maximum
					noteCause(new TimeoutException("Timeout"));

					// close connection, channelInactive will be called next
					ctx.close();
				}
			}
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			if (!this.success) {
				// remove this handler from pipeline so that it can be reused
				//ctx.pipeline().remove(this);

				// fail, may try again
				fail(this);
			}

			super.channelInactive(ctx);

			// remove channel from set of active channels
			synchronized (channels) {
				channels.remove(ctx.channel());
			}
		}

		/**
		 * Increments the retry count and returns true if failed because maximum retry count has been reached
		 * @return true if failed because maximum retry count has been reached
		 */
		public abstract boolean retry(int maxRetryCount);

	}

	/**
	 * @return true if the status code indicates failure but a retry makes sense
	 */
	public static boolean isRetryCode(int responseCode) {
		return responseCode == 400
				|| responseCode == 408
				|| responseCode == 429;
	}

	protected abstract class RequestHandler extends Handler<FullHttpResponse> {
		int retryCount = 0;

		@Override
		public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
			// pipeline gets built
			ctx.pipeline().addBefore("handler", "aggregator", new HttpObjectAggregator(1000000));

			super.handlerAdded(ctx);
		}


		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// connection is established: send http start to server

			// build http start with empty content
			FullHttpRequest request = getRequest();
			HttpHeaders headers = request.headers();
			headers.set(HttpHeaders.Names.HOST, host);
			cloud.extendRequest(request, configuration);

			// send the http start
			ctx.writeAndFlush(request);

			super.channelActive(ctx);
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
			// get http response code
			int responseCode = response.getStatus().code();

			if (responseCode / 100 == 2) {
				// success
				success(response);
				this.success = true;

			} else {
				// http error (e.g. 400)
				//System.err.println(content.content().toString(HttpCloud.UTF_8));
				// transfer has failed, maybe retry is possible
				setFailed(isRetryCode(responseCode), new HttpException(responseCode));
			}
			ctx.close();
		}

		@Override
		public boolean retry(int maxRetryCount) {
			return ++this.retryCount >= maxRetryCount;
		}

		/**
		 * Gets called when the http start needs to be created
		 * @return http start with content
		 */
		abstract protected FullHttpRequest getRequest() throws Exception;

		/**
		 * Gets called when the http get start was successful
		 */
		abstract protected void success(FullHttpResponse response) throws Exception;
	}


	public HttpFuture(HttpCloud cloud, Configuration configuration, String host, boolean https) {
		this.cloud = cloud;
		this.configuration = configuration;
		this.host = host;
		this.https = https;
	}

	// java.util.concurrent.Future<V>

	@Override
	public synchronized boolean cancel(boolean mayInterruptIfRunning) {
		if (this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED)
			return false;

		// close all channels
		for (Channel channel : this.channels) {
			channel.close();
		}

		setDone(Transfer.State.CANCELLED);

		return true;
	}

	@Override
	public synchronized boolean isCancelled() {
		return this.state == Transfer.State.CANCELLED;
	}

	@Override
	public synchronized boolean isDone() {
		return this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED;
	}

	@Override
	public synchronized V get() throws InterruptedException, ExecutionException {
		// wait until done
		while (!(this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED))
			wait();

		// act according to state
		if (this.state == Transfer.State.SUCCESS)
			return this.value;
		else if (this.state == Transfer.State.FAILED)
			throw new ExecutionException(this.cause);
		throw new CancellationException();
	}

	@Override
	public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		long startMillis = System.currentTimeMillis();
		long timeoutMillis = unit.toMillis(timeout);

		// wait until timeout or done
		long passedMillis;
		while ((passedMillis = System.currentTimeMillis() - startMillis) < timeoutMillis
				&& !(this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED)) {
			wait(timeoutMillis - passedMillis);
		}

		// act according to state
		if (this.state == Transfer.State.SUCCESS)
			return this.value;
		else if (this.state == Transfer.State.FAILED)
			throw new ExecutionException(this.cause);
		throw new CancellationException();
	}

	// io.netty.util.concurrent.Future<V>

	@Override
	public synchronized boolean isSuccess() {
		return this.state == Transfer.State.SUCCESS;
	}

	@Override
	public boolean isCancellable() {
		return true;
	}

	@Override
	public synchronized Throwable cause() {
		return this.cause;
	}

	@Override
	public synchronized Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
		GenericFutureListener<HttpFuture<V>> l = (GenericFutureListener<HttpFuture<V>>) listener;
		if (this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED) {
			// complete: call listener immediately
			try {
				l.operationComplete(this);
			} catch (Exception e) {
				// set state to FAILED if operationComplete() throws an exception
				this.state = Transfer.State.FAILED;
				this.cause = e;
			}
		} else {
			// add listener to listener list
			this.listeners.add(l);
		}
		return this;
	}

	@Override
	public Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
		for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {
			addListener(listener);
		}
		return this;
	}

	@Override
	public synchronized Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
		this.listeners.remove(listener);
		return this;
	}

	@Override
	public synchronized Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
		for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {
			this.listeners.remove(listener);
		}
		return this;
	}

	@Override
	public synchronized Future<V> sync() throws InterruptedException {
		// wait until done
		while (!(this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED))
			wait();

		//if (this.state == Transfer.State.FAILED)
		//	throw this.cause;
		return this;
	}

	@Override
	public synchronized Future<V> syncUninterruptibly() {
		// wait until done
		while (!(this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED)) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}

		//if (this.state == Transfer.State.FAILED)
		//		throw this.cause;
		return this;
	}

	@Override
	public synchronized Future<V> await() throws InterruptedException {
		// wait until done
		while (!(this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED))
			wait();
		return this;
	}

	@Override
	public synchronized Future<V> awaitUninterruptibly() {
		// wait until done
		while (!(this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED)) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
		return this;
	}

	@Override
	public synchronized boolean await(long timeout, TimeUnit unit) throws InterruptedException {
		return await(unit.toMillis(timeout));
	}

	@Override
	public synchronized boolean await(long timeoutMillis) throws InterruptedException {
		long startMillis = System.currentTimeMillis();

		// wait until timeout or done
		long passedMillis;
		while ((passedMillis = System.currentTimeMillis() - startMillis) < timeoutMillis
				&& !(this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED)) {
			wait(timeoutMillis - passedMillis);
		}

		// return true if success
		return this.state == Transfer.State.SUCCESS;
	}

	@Override
	public synchronized boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
		return awaitUninterruptibly(unit.toMillis(timeout));
	}

	@Override
	public synchronized boolean awaitUninterruptibly(long timeoutMillis) {
		long startMillis = System.currentTimeMillis();

		// wait until timeout or done
		long passedMillis;
		while ((passedMillis = System.currentTimeMillis() - startMillis) < timeoutMillis
				&& !(this.state == Transfer.State.SUCCESS || this.state == Transfer.State.FAILED || this.state == Transfer.State.CANCELLED)) {
			try {
				wait(timeoutMillis - passedMillis);
			} catch (InterruptedException e) {
			}
		}

		// return true if success
		return this.state == Transfer.State.SUCCESS;
	}

	@Override
	public synchronized V getNow() {
		return this.value;
	}

	// helpers

	protected synchronized void setState(Transfer.State state) {
		// set state and notify all waiting threads
		this.state = state;
		notifyAll();
	}

	public synchronized Transfer.State getState() {
		return this.state;
	}

	public void connect(final Handler handler) {
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
			if (this.https)
				pipeline.addLast("ssl", this.cloud.sslCtx.newHandler(channel.alloc()));

			// timeout handler (throws ReadTimeoutException which appears in exceptionCaught() of Handler)
			//pipeline.addLast("timeout", new ReadTimeoutHandler(timeout));
			pipeline.addLast("timeout", new IdleStateHandler(0, 0, timeout));

			// http codec
			pipeline.addLast("http", new HttpClientCodec());

			// gzip decompressor
			pipeline.addLast("decompressor", new HttpContentDecompressor());

			// our handler for HTTP messages
			pipeline.addLast("handler", handler);
		}

		// add channel to set of active channels
		synchronized (this.channels) {
			this.channels.add(channel);
		}

		// connect
		SocketAddress localAddress = null;
		SocketAddress remoteAddress = new InetSocketAddress(this.host, this.https ? HTTPS_PORT : HTTP_PORT);
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
				noteCause(future.cause());
				//pipeline.remove(handler);
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

	protected void fail(Handler handler) {
		// check if transfer is already done (failed or cancelled)
		if (isDone())
			return;

		// check if number of retries exceeded
		if (handler.retry(this.configuration.retryCount)) {
			// transfer has failed
			setFailed(this.notedCause);
			return;
		}

		// notify change of
		//stateChange();

		// retry after a delay
		int delay = this.configuration.timeout;
		this.cloud.timer.newTimeout((timeout) -> connect(handler), delay, TimeUnit.SECONDS);
	}

	synchronized protected void setFailed(Throwable cause) {
		// set cause
		this.cause = cause;

		// close all channels
		synchronized (this.channels) {
			for (Channel channel : this.channels) {
				channel.close();
			}
			this.channels.clear();
		}

		setDone(Transfer.State.FAILED);
	}

	synchronized protected void noteCause(Throwable cause) {
		this.notedCause = cause;
	}

	protected void setFailed(boolean retry, Throwable cause) {
		if (retry)
			noteCause(cause);
		else
			setFailed(cause);
	}

	synchronized protected void setSuccess(V value) {
		// set value
		this.value = value;

		setDone(Transfer.State.SUCCESS);
	}

	// set to done state (SUCCESS, FAILED or CANCELLED). The caller must synchronize on this
	protected void setDone(Transfer.State state) {
		// set state
		this.state = state;

		// notify listeners
		for (GenericFutureListener<HttpFuture<V>> listener : listeners) {
			try {
				listener.operationComplete(this);
			} catch (Exception e) {
				// set state to FAILED if operationComplete() throws an exception
				this.state = Transfer.State.FAILED;
				this.cause = e;
			}
		}

		// notify all waiting threads
		notifyAll();
	}

	protected synchronized void stateChange() {
		notifyAll();
	}
}
