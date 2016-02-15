package it.geenee.cloud.aws;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.concurrent.Future;
import it.geenee.cloud.Compute;
import it.geenee.cloud.Configuration;
import it.geenee.cloud.InstanceInfo;
import it.geenee.cloud.http.HttpCloud;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AwsCompute implements Compute {
	final AwsCloud cloud;
	final Configuration configuration;
	final String host;

	AwsCompute(AwsCloud cloud, Configuration configuration, String host) {
		this.cloud = cloud;
		this.configuration = configuration;
		this.host = host;
	}

	@Override
	public Future<Map<String, String>> startGetTags(String resourceId) {
		// http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeTags.html
		final String pathAndQuery = "/?Action=DescribeTags&Filter.1.Name=resource-id&Filter.1.Value.1=" + resourceId + AwsCloud.EC2_QUERY;
		final Map<String, String> map = new HashMap<>();
		return new AwsRequest<Map<String, String>>(this.cloud, this.configuration, this.host, HttpMethod.GET, pathAndQuery) {
			@Override
			protected void success(ByteArrayInputStream content) throws Exception {
				// parse xml
				JAXBContext jc = JAXBContext.newInstance(DescribeTagsResponse.class);
				Unmarshaller unmarshaller = jc.createUnmarshaller();
				DescribeTagsResponse response = (DescribeTagsResponse) unmarshaller.unmarshal(content);

				// copy tags to map
				if (response.tagSet != null && response.tagSet.items != null) {
					for (DescribeTagsResponse.Item item : response.tagSet.items) {
						map.put(item.key, item.value);
					}
				}

				// either repeat or finished
				if (response.nextToken != null) {
					connect(new ListHandler(HttpMethod.GET, HttpCloud.addQuery(pathAndQuery, "NextToken", response.nextToken)));
				} else {
					setSuccess(map);
				}
			}
		};
	}

	@Override
	public Instances instances() {
		return new Instances() {
			int index = 1;
			StringBuilder filters = new StringBuilder();

			@Override
			public Instances filter(String key, String... values) {
				this.filters.append("&Filter.").append(this.index).append(".Name=").append(HttpCloud.encodeUrl(key));
				int i = 1;
				for (String value : values) {
					this.filters.append("&Filter.").append(this.index).append(".Value.").append(i).append('=').append(HttpCloud.encodeUrl(value));
					++i;
				}
				++this.index;
				return this;
			}

			@Override
			public Instances filterZone(String... zones) {
				return filter("availability-zone", zones);
			}

			@Override
			public Instances filterTag(String key, String... values) {
				return filter("tag:" + key, values);
			}

			@Override
			public Instances filterTagKey(String... keys) {
				return filter("tag-key", keys);
			}

			@Override
			public Instances filterTagValue(String... values) {
				return filter("tag-value", values);
			}

			@Override
			public Future<List<InstanceInfo>> request() {
				// http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeInstances.html
				final String pathAndQuery = "/?Action=DescribeInstances" + this.filters + AwsCloud.EC2_QUERY;
				final List<InstanceInfo> list = new ArrayList<>();
				return new AwsRequest<List<InstanceInfo>>(cloud, configuration, host, HttpMethod.GET, pathAndQuery) {
					@Override
					protected void success(ByteArrayInputStream content) throws Exception {
						// parse xml
						JAXBContext jc = JAXBContext.newInstance(DescribeInstancesResponse.class);
						Unmarshaller unmarshaller = jc.createUnmarshaller();
						DescribeInstancesResponse response = (DescribeInstancesResponse) unmarshaller.unmarshal(content);

						// copy instance infos to list
						if (response.reservationSet != null && response.reservationSet.items != null) {
							for (DescribeInstancesResponse.ReservationItem reservation : response.reservationSet.items) {
								if (reservation.instancesSet != null && reservation.instancesSet.items != null) {
									for (DescribeInstancesResponse.InstanceItem instance : reservation.instancesSet.items) {
										String zone = null;
										String region = null;
										if (instance.placement != null) {
											zone = instance.placement.availabilityZone;
											region = zone.substring(0, zone.length()-1);
										}
										list.add(new InstanceInfo(
												instance.instanceId,
												reservation.reservationId,
												zone,
												region,
												instance.privateIpAddress,
												instance.ipAddress));
									}
								}
							}
						}

						// either repeat or finished
						if (response.nextToken != null) {
							connect(new ListHandler(HttpMethod.GET, HttpCloud.addQuery(pathAndQuery, "NextToken", response.nextToken)));
						} else {
							setSuccess(list);
						}
					}
				};
			}
		};
	}

}
