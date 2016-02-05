package it.geenee.cloud.aws;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpException;
import it.geenee.cloud.http.HttpQuery;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

/**
 * AWS describe tags
 * http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeTags.html
 */
public class AwsDescribeTags extends HttpQuery<Map<String, String>> {

	// channel handler to upload file via PUT
	class TagsHandler extends HttpQuery.Handler {
		String resourceId;

		int retryCount = 0;

		// http content received in response from server
		byte[] content;


		TagsHandler(String resourceId) {
			this.resourceId = resourceId;
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// connection is established: send http request to server

			// build uri for describe tags (http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeTags.html)
			String uri = "/?Action=DescribeTags&Filter.1.Name=resource-id&Filter.1.Value.1=" + this.resourceId + "&Version=2015-10-01";

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
						JAXBContext jc = JAXBContext.newInstance(DescribeTagsResponse.class);
						Unmarshaller unmarshaller = jc.createUnmarshaller();
						DescribeTagsResponse response = (DescribeTagsResponse) unmarshaller.unmarshal(new ByteArrayInputStream(this.content));

						// describe tags done
						Map<String, String> map = new HashMap<>();
						if (response.tagSet != null && response.tagSet.items != null) {
							for (DescribeTagsResponse.Item item : response.tagSet.items) {
								map.put(item.key, item.value);
							}
						}
						setSuccess(map);

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

	public AwsDescribeTags(HttpCloud cloud, String host, String resourceId, Configuration configuration) {
		super(cloud, host, configuration, true);


		connect(new TagsHandler(resourceId));
	}
}
