package it.geenee.cloud.aws;

import io.netty.handler.codec.http.*;
import it.geenee.cloud.Configuration;
import it.geenee.cloud.FileInfo;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpFuture;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * Get file info for a file on S3 by using a HEAD request
 */
class AwsGetFileInfo extends HttpFuture<FileInfo> {

	public AwsGetFileInfo(HttpCloud cloud, Configuration configuration, String host, final String remotePath, final String requestedVersion) {
		super(cloud, configuration, host, true);

		String urlPath = HttpCloud.encodePath('/' + configuration.prefix + remotePath);
		final String urlPathAndVersion = cloud.addVersion(urlPath, requestedVersion);

		connect(new RequestHandler() {
			@Override
			protected FullHttpRequest getRequest() throws Exception {
				return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, urlPathAndVersion);
			}

			@Override
			protected void success(FullHttpResponse response) throws Exception {
				HttpHeaders headers = response.headers();

				String hash = cloud.getHash(headers);
				long size = Long.parseLong(headers.get("Content-Length"));
				long timestamp = HttpHeaders.getDateHeader(response, "Last-Modified").getTime(); // https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1
				String version = cloud.getVersion(headers);

				setSuccess(new FileInfo(remotePath, hash, size, timestamp, version, requestedVersion == null));
			}
		});
	}
}
