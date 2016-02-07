package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeInstances.html
 */
@XmlRootElement(name = "DescribeInstancesResponse", namespace = "http://ec2.amazonaws.com/doc/2015-10-01/")
@XmlAccessorType(XmlAccessType.NONE)
public class DescribeInstancesResponse {
	@XmlElement(name = "requestId")
	public String requestId;

	public static class Placement {
		@XmlElement(name = "availabilityZone")
		public String availabilityZone;
	}

	public static class InstanceItem {
		@XmlElement(name = "instanceId")
		public String instanceId;

		@XmlElement(name = "imageId")
		public String imageId;

		@XmlElement(name = "placement")
		public Placement placement;

		@XmlElement(name = "privateIpAddress")
		public String privateIpAddress;

		@XmlElement(name = "ipAddress")
		public String ipAddress;
	}

	public static class InstancesSet {
		@XmlElement(name = "item")
		public List<InstanceItem> items;
	}

	public static class ReservationItem {
		@XmlElement(name = "reservationId")
		public String reservationId;

		@XmlElement(name = "ownerId")
		public String ownerId;


		@XmlElement(name = "instancesSet")
		public InstancesSet instancesSet;
	}

	public static class ReservationSet {
		@XmlElement(name = "item")
		public List<ReservationItem> items;
	}

	@XmlElement(name = "reservationSet")
	public ReservationSet reservationSet;
}
