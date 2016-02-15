package it.geenee.cloud.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.HashedWheelTimer;
import it.geenee.cloud.Cloud;
import it.geenee.cloud.Configuration;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Arrays;

public abstract class HttpCloud implements Cloud {

	// configuration
	protected final Configuration configuration;

	public final SslContext sslCtx;
	public final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
	public final HashedWheelTimer timer = new HashedWheelTimer();


	/**
	 * Constructor
	 * @param configuration global configuration
	 * @throws SSLException
	 */
	public HttpCloud(Configuration configuration) throws SSLException {
		this.configuration = configuration;

		this.sslCtx = SslContextBuilder.forClient()
				.trustManager(InsecureTrustManagerFactory.INSTANCE).build();
	}

	// helpers

	public static final Charset UTF_8 = Charset.forName("UTF-8");

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
	 * Get version parameter that is used to request a specific version
	 * @return version parameter
	 */
	public abstract String getVersionParameter();

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

	public static String addQuery(String pathAndQuery, String key, String value) {
		return pathAndQuery + (pathAndQuery.indexOf('?') == -1 ? '?' : '&') + key + '=' + encodeUrl(value);
	}
	public static String addQuery(String pathAndQuery, String key, int value) {
		return pathAndQuery + (pathAndQuery.indexOf('?') == -1 ? '?' : '&') + key + '=' + value;
	}
	public static String addQuery(String pathAndQuery, String key) {
		return pathAndQuery + (pathAndQuery.indexOf('?') == -1 ? '?' : '&') + key;
	}

	public static byte[] collectContent(byte[] data, HttpContent content) {
		ByteBuf buf = content.content();
		int readableBytes = buf.readableBytes();
		if (data == null) {
			data = new byte[readableBytes];
			buf.readBytes(data);
		} else if (readableBytes > 0) {
			int position = data.length;
			data = Arrays.copyOf(data, position + readableBytes);
			buf.readBytes(data, position, readableBytes);
		}
		return data;
	}
}
