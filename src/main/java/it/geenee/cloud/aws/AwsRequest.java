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

	public AwsRequest(HttpCloud cloud, Cloud.Configuration configuration, String host, HttpMethod method, String urlPath) {
		super(cloud, configuration, host, true);
		request(method, urlPath);
	}

	void request(HttpMethod method, String urlPath) {
		connect(new RequestHandler() {
			@Override
			protected FullHttpRequest getRequest() throws Exception {
				return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, urlPath);
			}

			@Override
			protected void success(HttpResponse response) throws Exception {
				//System.out.println(response.content().toString(HttpCloud.UTF_8));
				AwsRequest.this.success(getContent());
			}
		});
	}

	protected abstract void success(InputStream content) throws Exception;
}
