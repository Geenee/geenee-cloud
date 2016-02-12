package it.geenee.cloud.aws;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpFuture;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

/**
 * AWS describe tags
 * http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeTags.html
 */
public class AwsDescribeTags extends HttpFuture<Map<String, String>> {

	class TagsHandler extends HttpFuture.GetHandler {
		TagsHandler(String resourceId) {
			super("/?Action=DescribeTags&Filter.1.Name=resource-id&Filter.1.Value.1=" + resourceId + AwsCloud.EC2_QUERY);
		}

		@Override
		protected void success(byte[] content) throws Exception {
			// parse xml
			//System.out.println(new String(this.content, HttpCloud.UTF_8));
			JAXBContext jc = JAXBContext.newInstance(DescribeTagsResponse.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			DescribeTagsResponse response = (DescribeTagsResponse) unmarshaller.unmarshal(new ByteArrayInputStream(content));

			// describe tags done
			Map<String, String> map = new HashMap<>();
			if (response.tagSet != null && response.tagSet.items != null) {
				for (DescribeTagsResponse.Item item : response.tagSet.items) {
					map.put(item.key, item.value);
				}
			}
			setSuccess(map);
		}
	}


	public AwsDescribeTags(HttpCloud cloud, String host, String resourceId, Configuration configuration) {
		super(cloud, host, configuration, true);

		connect(new TagsHandler(resourceId));
	}
}
