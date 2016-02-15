package it.geenee.cloud.aws;

import java.io.*;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpFuture;

/**
 * Base class for simple requests like list bucket objects, delete object and describe instances
 */
abstract public class AwsRequest<V> extends HttpFuture<V> {

	class ListHandler extends RequestHandler {
		HttpMethod method;
		String pathAndQuery;

		ListHandler(HttpMethod method, String pathAndQuery) {
			//super(method, pathAndQuery);
			this.method = method;
			this.pathAndQuery = pathAndQuery;
		}

		@Override
		protected FullHttpRequest getRequest() throws Exception {
			return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, this.method, this.pathAndQuery);
		}

		@Override
		protected void success(byte[] content) throws Exception {
			//System.out.println(new String(content, HttpCloud.UTF_8));
			AwsRequest.this.success(new ByteArrayInputStream(content));
		}
	}

	public AwsRequest(HttpCloud cloud, Configuration configuration, String host, HttpMethod method, String pathAndQuery) {
		super(cloud, configuration, host, true);
		connect(new ListHandler(method, pathAndQuery));
	}

	protected abstract void success(ByteArrayInputStream content) throws Exception;
}
