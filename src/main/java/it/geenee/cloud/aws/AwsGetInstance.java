package it.geenee.cloud.http;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;

/**
 * AWS get instance data of the instance we are currently running on
 * http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html
 */
public class AwsGetInstance extends HttpQuery<Instance> {
	public static final String HOST = "instance-data";
	public static final String INSTANCE_ID = "/latest/meta-data/instance-id";
	public static final String ZONE = "/latest/meta-data/placement/availability-zone";

	String instanceId = null;
	String zone = null;

	// channel handler to upload file via PUT
	class GetHandler extends HttpQuery.Handler {
		String path;

		int retryCount = 0;

		// http content received in response from server
		byte[] content;


		GetHandler(String path) {
			this.path = path;
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// connection is established: send http request to server

			// build http request (without content as we send it on receiving continue 100 status code)
			FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, this.path);
			HttpHeaders headers = request.headers();
			headers.set(HttpHeaders.Names.HOST, host);

			// send the http request
			ctx.writeAndFlush(request);

			super.channelActive(ctx);
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
			if (msg instanceof HttpResponse) {
				HttpResponse response = (HttpResponse) msg;

				// get http response code
				this.responseCode = response.getStatus().code();
			} else if (msg instanceof HttpContent) {
				HttpContent content = (HttpContent) msg;

				if (this.responseCode / 100 == 2) {
					// http request succeeded: collect content
					this.content = HttpCloud.collectContent(this.content, content);

					if (content instanceof LastHttpContent) {
						done(this.path, new String(this.content, HttpCloud.UTF_8));

						ctx.close();
					}
				} else {
					// http error (e.g. 400)
					//System.err.println(content.content().toString(HttpCloud.UTF_8));
					if (content instanceof LastHttpContent) {
						ctx.close();

						// transfer has failed, maybe retry is possible
						setFailed(isRetryCode(), new HttpException(this.responseCode));
					}
				}
			}
		}

		@Override
		protected boolean hasFailed() {
			// if state is not success (e.g. on read timeout), describe tags has failed
			return !isSuccess();
		}

		@Override
		public boolean retry(int maxRetryCount) {
			return ++this.retryCount >= maxRetryCount;
		}
	}

	public AwsGetInstance(HttpCloud cloud, Configuration configuration) {
		super(cloud, HOST, configuration, false); // use http

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
			setSuccess(new Instance(
					this.instanceId,
					this.zone,
					this.zone.substring(0, this.zone.length() - 1)
			));
		}
	}
}
