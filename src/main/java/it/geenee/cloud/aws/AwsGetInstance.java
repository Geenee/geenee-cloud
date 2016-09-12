package it.geenee.cloud.aws;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;

/**
 * Get instance info of the instance we are currently running on
 * http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html
 */
public class AwsGetInstance extends AwsGetMetadata<InstanceInfo> {
	public static final String INSTANCE_ID = "/latest/meta-data/instance-id";
	public static final String RESERVATION_ID = "/latest/meta-data/reservation-id";
	public static final String AVAILABILITY_ZONE = "/latest/meta-data/placement/availability-zone";
	public static final String LOCAL_IPV4 = "/latest/meta-data/local-ipv4";
	public static final String PUBLIC_IPV4 = "/latest/meta-data/public-ipv4";

	String instanceId = null;
	String groupId = null;
	String zone = null;
	String privateIpAddress = null;
	String ipAddress = null;

	public AwsGetInstance(HttpCloud.Globals globals) {
		super(globals, INSTANCE_ID);
	}

	protected void done(String path, String value) {
		switch (path) {
			case INSTANCE_ID:
				this.instanceId = value;
				startGet(RESERVATION_ID);
				break;
			case RESERVATION_ID:
				this.groupId = value;
				startGet(AVAILABILITY_ZONE);
				break;
			case AVAILABILITY_ZONE:
				this.zone = value;
				startGet(LOCAL_IPV4);
				break;
			case LOCAL_IPV4:
				this.privateIpAddress = value;
				startGet(PUBLIC_IPV4);
				break;
			case PUBLIC_IPV4:
				this.ipAddress = value;
				// set result
				setSuccess(new InstanceInfo(
						this.instanceId,
						this.groupId,
						this.zone,
						this.zone.substring(0, this.zone.length() - 1),
						this.privateIpAddress,
						this.ipAddress
				));
				break;
		}
	}
}
