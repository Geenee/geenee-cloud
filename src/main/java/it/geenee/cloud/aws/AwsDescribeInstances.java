package it.geenee.cloud.aws;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpFuture;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

/**
 * AWS describe instances
 * http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeInstances.html
 */
public class AwsDescribeInstances extends HttpFuture<List<Instance>> {

	class InstancesHandler extends HttpFuture.GetHandler {
		InstancesHandler(String filters) {
			super("/?Action=DescribeInstances" + filters + AwsCloud.EC2_QUERY);
		}

		@Override
		protected void success(byte[] content) throws Exception {
			// parse xml
			//System.out.println(new String(this.content, HttpCloud.UTF_8));
			JAXBContext jc = JAXBContext.newInstance(DescribeInstancesResponse.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			DescribeInstancesResponse response = (DescribeInstancesResponse) unmarshaller.unmarshal(new ByteArrayInputStream(content));

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
		}
	}

	public AwsDescribeInstances(HttpCloud cloud, String host, String filters, Configuration configuration) {
		super(cloud, host, configuration, true);

		connect(new InstancesHandler(filters));
	}
}
