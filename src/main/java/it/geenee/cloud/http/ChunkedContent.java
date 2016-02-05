package it.geenee.cloud;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedInput;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * A {@link ChunkedInput} that fetches data from a file channel chunk by chunk.
 * Derived from io.netty.handler.stream.ChunkedFile and io.netty.handler.codec.http.HttpChunkedInput
 */
public class ChunkedContent implements ChunkedInput<HttpContent> {

	private final FileChannel file;
	private final long offset;
	private final long length;
	private final int chunkSize;

	private long position;
	private boolean sentLastChunk;

	/**
	 * Creates a new instance that fetches data from the specified file channel.
	 *
	 * @param file the file channel to read from. Only reads with explicit position are used, therefore it can be used
	 * by multiple ChunkedContents at the same time.
	 * @param chunkSize the number of bytes to fetch on each
	 * {@link #readChunk(ChannelHandlerContext)} call
	 */
	public ChunkedContent(FileChannel file, int chunkSize) throws IOException {
		this(file, 0, file.size(), chunkSize);
	}

	/**
	 * Creates a new instance that fetches data from the specified file channel.
	 *
	 * @param file the file channel to read from. Only reads with explicit position are used, therefore it can be used
	 * by multiple ChunkedContents at the same time.
	 * @param offset the offset of the file where the transfer begins
	 * @param length the number of bytes to transfer
	 * @param chunkSize the number of bytes to fetch on each
	 * {@link #readChunk(ChannelHandlerContext)} call (must be at least 256)
	 */
	public ChunkedContent(FileChannel file, long offset, long length, int chunkSize) throws IOException {
		assert file != null : "file must not be null";
		assert offset >= 0 : "offset must be >= 0";
		assert length > 0 : "length must be > 0";
		assert chunkSize >= 256 : "chunkSize must be >= 256";

		this.file = file;
		this.offset = offset;
		this.length = length;
		this.chunkSize = chunkSize;
	}

	@Override
	public boolean isEndOfInput() throws Exception {
		// Only end of input after last HTTP chunk has been sent
		return this.position >= this.length && this.sentLastChunk;
	}

	@Override
	public void close() throws Exception {
		// we are not responsible for closing the file here because it can be transferred in parallel by multiple channels
	}

	@Override
	public HttpContent readChunk(ChannelHandlerContext ctx) throws Exception {
		if (this.position >= this.length) {
			if (this.sentLastChunk) {
				return null;
			} else {
				// send last chunk for this input
				sentLastChunk = true;
				return LastHttpContent.EMPTY_LAST_CONTENT;
			}
		} else {
			int bufferSize = (int) Math.min(this.chunkSize, this.length - position);
			ByteBuf buffer = ctx.alloc().heapBuffer(bufferSize);

			boolean release = true;
			try {
				buffer.writerIndex(bufferSize);
				//System.out.println("read o: " + (this.offset + this.position) + " s: " + bufferSize);
				this.file.read(buffer.nioBuffer(), this.offset + this.position);
				this.position += bufferSize;
				release = false;
				return new DefaultHttpContent(buffer);
			} finally {
				if (release) {
					buffer.release();
				}
			}
		}
	}
}
