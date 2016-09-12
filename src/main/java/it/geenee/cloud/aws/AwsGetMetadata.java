package it.geenee.cloud.aws;

import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpFuture;

/**
 * Get instance metadata of the instance we are currently running on
 * http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html
 */
abstract public class AwsGetMetadata<V> extends HttpFuture<V> {
	/**
	 * http://instance-data is an aws-internal http server
	 * to simulate on local host:
	 * - add instance-data to /etc/hosts file and let it point to localhost
	 * - cd to geenee-cloud/src/test/instance-data
	 * - sudo python -m SimpleHTTPServer 80
 	 */
	public static final String HOST = "instance-data";

	public AwsGetMetadata(HttpCloud.Globals globals, String path) {
		// use default configuration because an aws internal http server asked and no credentials are needed
		super(globals, AwsCloud.DEFAULT_CONFIGURATION, HOST, false); // use http

		// connect to server to get metadata at given path
		startGet(path);
	}

	void startGet(String path) {
		connect(new RequestHandler() {
			@Override
			protected FullHttpRequest getRequest() throws Exception {
				return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
			}

			@Override
			protected void success(HttpResponse response) throws Exception {
				done(path, getContentAsString());
			}
		});
	}

	// gets called when metadata was obtained
	abstract protected void done(String path, String value);
}
