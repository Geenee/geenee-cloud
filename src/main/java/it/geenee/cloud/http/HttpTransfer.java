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

	protected final FileChannel file;
	protected final String remotePath;

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


	public HttpTransfer(HttpCloud cloud, Configuration configuration, FileChannel file, String host, String remotePath) {
		super(cloud, configuration, host, true);

		this.file = file;
		this.remotePath = remotePath;
	}

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
}
