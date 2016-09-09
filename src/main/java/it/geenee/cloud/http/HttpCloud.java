package it.geenee.cloud.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.HashedWheelTimer;
import it.geenee.cloud.Cloud;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;


public abstract class HttpCloud implements Cloud {
	// container for global instances
	public static class Globals {
		public final SslContext sslCtx;
		public final EventLoopGroup eventLoopGroup;
		public final Class<? extends SocketChannel> channelClass;
		public final HashedWheelTimer timer;

		public Globals(SslContext sslCtx, EventLoopGroup eventLoopGroup, Class<? extends SocketChannel> channelClass,
				HashedWheelTimer timer) {
			this.sslCtx = sslCtx;
			this.eventLoopGroup = eventLoopGroup;
			this.channelClass = channelClass;
			this.timer = timer;
		}
	}

	// global instances that can be shared with other parts of an application
	public final Globals globals;

	// configuration
	public final Configuration configuration;

	/**
	 * Constructor
	 * @param globals global instances of objects that can be shared
	 * @param configuration global configuration
	 */
	public HttpCloud(Globals globals, Configuration configuration) {
		this.globals = globals;
		this.configuration = configuration;
	}

	// helpers

	public static final Charset UTF_8 = Charset.forName("UTF-8");

	public static Globals createGobals() throws SSLException {
		return new Globals(
				SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build(),
				new NioEventLoopGroup(),
				NioSocketChannel.class,
				new HashedWheelTimer());
	}


	/**
	 * Calculate hash of given file range
	 * @param file
	 * @param offset
	 * @param length
	 * @return hash of given part of file
	 * @throws Exception
	 */
	public static byte[] hash(String algorithm, FileChannel file, long offset, long length) throws Exception {
		ByteBuffer buffer = ByteBuffer.allocate(16384);

		// calc hash
		MessageDigest md = MessageDigest.getInstance(algorithm);
		while (length > 0) {
			// read from file
			buffer.rewind();
			buffer.limit((int)Math.min(length, (long)buffer.capacity()));
			int readCount = file.read(buffer, offset);
			if (readCount <= 0)
				break;

			// update hash
			buffer.rewind();
			md.update(buffer);

			// update offset and length
			offset += readCount;
			length -= readCount;
		}
		return md.digest();
	}

	public static byte[] md5(FileChannel file, long offset, long length) throws Exception {
		return hash("MD5", file, offset, length);
	}

	public static byte[] sha256(FileChannel file, long offset, long length) throws Exception {
		return hash("SHA-256", file, offset, length);
	}

	public static byte[] sha256(byte[] data) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return digest.digest(data);
	}

	public static byte[] sha256(byte[] data, int offset, int length) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(data, offset, length);
		return digest.digest();
	}

	public static byte[] sha256(String data) throws Exception {
		return sha256(data.getBytes(UTF_8));
	}

	/**
	 * Calculate MAC (message authentication code) based on SHA-256 cryptographic hash function
	 * @param key
	 * @param data
	 * @return SHA-256 MAC
	 * @throws Exception
	 */
	public static byte[] sha256Mac(byte[] key, byte[] data) throws Exception {
		SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA256");
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(signingKey);
		return mac.doFinal(data);
	}

	public static byte[] sha256Mac(byte[] key, String data) throws Exception {
		return sha256Mac(key, data.getBytes(UTF_8));
	}

	public static byte[] sha256Mac(String key, String data) throws Exception {
		return sha256Mac(key.getBytes(UTF_8), data.getBytes(UTF_8));
	}

	/**
	 * Add specific request headers to request and sign request if credentials are present
	 * @param request http request including header and content
	 * @param configuration
	 * @throws Exception
	 */
	public void extendRequest(FullHttpRequest request, Configuration configuration) throws Exception {
		HttpHeaders headers = request.headers();
		headers.set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);

		ByteBuf content = request.content();
		int length = content.readableBytes();
		if (length > 0)
			headers.set(HttpHeaders.Names.CONTENT_LENGTH, length);
	}

	/**
	 * Add specific request headers to request and sign request if credentials are present
	 * @param request http request including only the header
	 * @param file content file
	 * @param offset offset in content file
	 * @param length content length
	 * @param configuration
	 * @throws Exception
	 */
	public void extendRequest(HttpRequest request, FileChannel file, long offset, long length, Configuration configuration) throws Exception {
		HttpHeaders headers = request.headers();
		headers.set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);

		headers.set(HttpHeaders.Names.CONTENT_LENGTH, length);
		headers.set(HttpHeaders.Names.CONTENT_MD5, Base64.encodeBase64String(md5(file, offset, length)));
	}

	/**
	 * Called when a http request fails. The cloud implementation can change the http status code based on the result
	 * body, e.g. if a 400 is returned but the reason reported in the body is 401 (unauthorized)
	 * @param statusCode http error status code
	 * @param body response body
	 * @return http error status code
	 */
	public abstract int fail(String host, int statusCode, InputStream body) throws Exception;

	/**
	 * Get hash of file from headers (e.g. from ETag)
	 * @param headers http headers
	 * @return hash of file
	 */
	public abstract String getHash(HttpHeaders headers);

	/**
	 * Get version id from headers
	 * @param headers http headers
	 * @return version id
	 */
	public abstract String getVersion(HttpHeaders headers);

	/**
	 * Add version parameter to given path part of an url
	 * @param urlPath path (and query) part of url to extend, e.g. "/foo/bar"
	 * @return path and query part of url extended by version parameter, e.g. "/foo/bar?versionId=12345"
	 */
	public abstract String addVersion(String urlPath, String version);

	static final char[] hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

	/**
	 * Encode a string for usage as an url accroding to https://en.wikipedia.org/wiki/Percent-encoding
	 * @param s string to encode (e.g. "some/path/foo bar")
	 * @return encoded string (e.g. "some%2Fpath%2Ffoo%20bar")
	 */
	public static String encodeUrl(String s) {
		// check if there is anything to encode
		int len = s.length();
		int i;
		for (i = 0; i < len; ++i) {
			char ch = s.charAt(i);
			if (!(ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '-' || ch == '_' || ch == '.'|| ch == '~'))
				break;
		}
		if (i == len)
			return s;

		// encode
		byte[] chars = s.getBytes(UTF_8);
		StringBuilder b = new StringBuilder();
		for (byte c : chars) {
			char ch = (char)(c & 0xff);
			if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '-' || ch == '_' || ch == '.'|| ch == '~') {
				b.append(ch);
			} else {
				b.append('%');
				b.append(hex[ch / 16]);
				b.append(hex[ch % 16]);
			}
		}
		return b.toString();
	}

	/**
	 * URL-Encode a string for usage as an url accroding to https://en.wikipedia.org/wiki/Percent-encoding
	 * @param s string to encode (e.g. "some/path/foo bar")
	 * @return encoded string (e.g. "some%2Fpath%2Ffoo%20bar")
	 */
	public static String encodePath(String s) {
		// check if there is anything to encode
		int len = s.length();
		int i;
		for (i = 0; i < len; ++i) {
			char ch = s.charAt(i);
			if (!(ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '-' || ch == '_' || ch == '.' || ch == '~' || ch == '/'))
				break;
		}
		if (i == len)
			return s;

		// encode
		byte[] chars = s.getBytes(UTF_8);
		StringBuilder b = new StringBuilder();
		for (byte c : chars) {
			char ch = (char)(c & 0xff);
			if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '-' || ch == '_' || ch == '.' || ch == '~' || ch == '/') {
				b.append(ch);
			} else {
				b.append('%');
				b.append(hex[ch / 16]);
				b.append(hex[ch % 16]);
			}
		}
		return b.toString();
	}

	public static String addQuery(String urlPath, String key, String value) {
		return urlPath + (urlPath.indexOf('?') == -1 ? '?' : '&') + key + '=' + encodeUrl(value);
	}
	public static String addQuery(String urlPath, String key, int value) {
		return urlPath + (urlPath.indexOf('?') == -1 ? '?' : '&') + key + '=' + value;
	}
	public static String addQuery(String urlPath, String key) {
		return urlPath + (urlPath.indexOf('?') == -1 ? '?' : '&') + key;
	}
}
