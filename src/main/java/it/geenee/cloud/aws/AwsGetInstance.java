package it.geenee.cloud.aws;

import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpFuture;

/**
 * AWS get instance data of the instance we are currently running on
 * http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html
 */
public class AwsGetInstance extends HttpFuture<InstanceInfo> {
	public static final String HOST = "instance-data";
	public static final String INSTANCE_ID = "/latest/meta-data/instance-id";
	public static final String ZONE = "/latest/meta-data/placement/availability-zone";

	String instanceId = null;
	String zone = null;

	class GetHandler extends RequestHandler {
		String path;

		GetHandler(String path) {
			this.path = path;
		}

		@Override
		protected FullHttpRequest getRequest() throws Exception {
			return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, this.path);
		}

		@Override
		protected void success(HttpResponse response) throws Exception {
			done(this.path, getContentAsString());
		}
	}


	public AwsGetInstance(HttpCloud cloud) {
		// use default configuration because an aws internal http server asked and no credentials are needed
		super(cloud, AwsCloud.DEFAULT_CONFIGURATION, HOST, false); // use http

		connect(new GetHandler(INSTANCE_ID));
		connect(new GetHandler(ZONE));
	}

	protected void done(String path, String value) {
		switch (path) {
			case INSTANCE_ID:
				this.instanceId = value;
				break;
			case ZONE:
				this.zone = value;
				break;
		}

		if (this.instanceId != null && this.zone != null) {
			setSuccess(new InstanceInfo(
					this.instanceId,
					null,
					this.zone,
					this.zone.substring(0, this.zone.length() - 1),
					null,
					null
			));
		}
	}
}
