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
 * Created by wilhelmy on 29.02.16.
 */
class AwsGetFileInfo extends HttpFuture<FileInfo> {

	class GetInfoHandler extends RequestHandler {
		String remotePath;
		String version;

		GetInfoHandler(String remotePath, String version) {
			this.remotePath = remotePath;
			this.version = version;
		}

		@Override
		protected FullHttpRequest getRequest() throws Exception {
			String urlPath = HttpCloud.encodePath('/' + configuration.prefix + this.remotePath);
			String urlPathAndVersion = cloud.addVersion(urlPath, this.version);
			return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, urlPathAndVersion);
		}

		@Override
		protected void success(FullHttpResponse response) throws Exception {
			HttpHeaders headers = response.headers();

			String hash = cloud.getHash(headers);
			long size = Long.parseLong(headers.get("Content-Length"));
			Date timestamp = HttpHeaders.getDateHeader(response, "Last-Modified"); // https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1
			String version = cloud.getVersion(headers);

			setSuccess(new FileInfo(this.remotePath, hash, size, timestamp, version, this.version == null));
		}
	}

	public AwsGetFileInfo(HttpCloud cloud, Configuration configuration, String host, String remotePath, String version) {
		super(cloud, configuration, host, true);
		connect(new GetInfoHandler(remotePath, version));
	}
}
