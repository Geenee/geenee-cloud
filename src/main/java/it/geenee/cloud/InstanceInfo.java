package it.geenee.cloud;

/**
 * Information about an instance
 */
public class InstanceInfo {
	// id of the instance
	public final String instanceId;

	// id of the group of instances that were launched together (AWS: reservation id)
	public final String groupId;

	// zone: region and datacenter (AWS: avalability zone e.g. "us-east-1a")
	public final String zone;

	// region (AWS: e.g. "us-east-1")
	public final String region;

	// public and private ip address
	public final String privateIpAddress;
	public final String ipAddress;


	public InstanceInfo(String instanceId, String groupId, String zone, String region,
			String privateIpAddress, String ipAddress) {
		this.instanceId = instanceId;
		this.groupId = groupId;
		this.zone = zone;
		this.region = region;

		this.privateIpAddress = privateIpAddress;
		this.ipAddress = ipAddress;
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('{');
		Cloud.append(b, "instanceId", this.instanceId);
		Cloud.append(b, "groupId", this.groupId);
		Cloud.append(b, "zone", this.zone);
		Cloud.append(b, "region", this.region);
		Cloud.append(b, "privateIpAddress", this.privateIpAddress);
		Cloud.append(b, "ipAddress", this.ipAddress);
		b.append('}');
		return b.toString();
	}
}
