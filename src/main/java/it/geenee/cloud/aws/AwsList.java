package it.geenee.cloud.aws;

import java.io.*;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpFuture;

/**
 * Base class for listing functions like list bucket objects and describe instances
 */
abstract public class AwsList<V> extends HttpFuture<V> {

	class ListHandler extends HttpFuture.GetHandler {

		ListHandler(String pathAndQuery) {
			super(pathAndQuery);
		}

		@Override
		protected void success(byte[] content) throws Exception {
			//System.out.println(new String(content, HttpCloud.UTF_8));
			AwsList.this.success(new ByteArrayInputStream(content));
		}

	}

	public AwsList(HttpCloud cloud, Configuration configuration, String host, String pathAndQuery) {
		super(cloud, configuration, host, true);

		connect(new ListHandler(pathAndQuery));
	}

	protected abstract void success(ByteArrayInputStream content) throws Exception;
}
