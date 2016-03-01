package it.geenee.cloud.aws;

import java.io.*;

import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.*;
import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpFuture;

/**
 * Base class for simple requests like list bucket objects, delete object and describe instances
 */
abstract public class AwsRequest<V> extends HttpFuture<V> {

	class ListHandler extends RequestHandler {
		HttpMethod method;
		String urlPath;

		ListHandler(HttpMethod method, String urlPath) {
			this.method = method;
			this.urlPath = urlPath;
		}

		@Override
		protected FullHttpRequest getRequest() throws Exception {
			return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, this.method, this.urlPath);
		}

		@Override
		protected void success(FullHttpResponse response) throws Exception {
			//System.out.println(response.content().toString(HttpCloud.UTF_8));
			AwsRequest.this.success(new ByteBufInputStream(response.content()));
		}
	}

	public AwsRequest(HttpCloud cloud, Configuration configuration, String host, HttpMethod method, String urlPath) {
		super(cloud, configuration, host, true);
		connect(new ListHandler(method, urlPath));
	}

	protected abstract void success(InputStream content) throws Exception;
}
