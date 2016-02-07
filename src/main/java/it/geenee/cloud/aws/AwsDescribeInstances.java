package it.geenee.cloud.aws;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpException;
import it.geenee.cloud.http.HttpFuture;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

/**
 * AWS describe tags
 * http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeInstances.html
 */
public class AwsDescribeInstances extends HttpFuture<List<Instance>> {

	// channel handler to upload file via PUT
	class TagsHandler extends HttpFuture.Handler {
		String filters;

		int retryCount = 0;

		// http content received in response from server
		byte[] content;


		TagsHandler(String filters) {
			this.filters = filters;
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// connection is established: send http request to server

			// build uri for describe tags (http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeTags.html)
			String uri = "/?Action=DescribeInstances" + this.filters + AwsCloud.EC2_VERSION;

			// build http request
			FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
			HttpHeaders headers = request.headers();
			headers.set(HttpHeaders.Names.HOST, host);
			cloud.extendRequest(request, configuration);

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
						// parse xml
						//System.out.println(new String(this.content, HttpCloud.UTF_8));
						JAXBContext jc = JAXBContext.newInstance(DescribeInstancesResponse.class);
						Unmarshaller unmarshaller = jc.createUnmarshaller();
						DescribeInstancesResponse response = (DescribeInstancesResponse) unmarshaller.unmarshal(new ByteArrayInputStream(this.content));

						// describe instances done
						List<Instance> list = new ArrayList<>();
						if (response.reservationSet != null && response.reservationSet.items != null) {
							for (DescribeInstancesResponse.ReservationItem reservation : response.reservationSet.items) {
								if (reservation.instancesSet != null && reservation.instancesSet.items != null) {
									for (DescribeInstancesResponse.InstanceItem instance : reservation.instancesSet.items) {
										String zone = null;
										if (instance.placement != null)
											zone = instance.placement.availabilityZone;
										list.add(new Instance(
												instance.instanceId,
												reservation.reservationId,
												zone,
												zone.substring(0, zone.length()-1),
												instance.privateIpAddress,
												instance.ipAddress));
									}
								}
							}
						}
						setSuccess(list);

						ctx.close();
					}
				} else {
					// http error (e.g. 400)
					System.err.println(content.content().toString(HttpCloud.UTF_8));
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

	public AwsDescribeInstances(HttpCloud cloud, String host, String filters, Configuration configuration) {
		super(cloud, host, configuration, true);


		connect(new TagsHandler(filters));
	}
}
