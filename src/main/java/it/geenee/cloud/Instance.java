package it.geenee.cloud;

/**
 * Instance description
 */
public class Instance {
	// id of the instance
	public final String instanceId;

	// id of the group of instances that were launched together (AWS: reservation id)
	public final String groupId;

	// zone: region and datacenter (AWS: avalability zone e.g. "us-east-1a")
	public final String zone;

	// region (AWS: e.g. "us-east-1")
	public final String region;


	public final String privateIpAddress;
	public final String ipAddress;


	public Instance(String instanceId, String groupId, String zone, String region,
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
		append(b, "instanceId", this.instanceId);
		append(b, "groupId", this.groupId);
		append(b, "zone", this.zone);
		append(b, "region", this.region);
		append(b, "privateIpAddress", this.privateIpAddress);
		append(b, "ipAddress", this.ipAddress);
		b.append('}');
		return b.toString();
	}
	static StringBuilder append(StringBuilder b, String key, String value) {
		if (b.length() > 1)
			b.append(", ");
		b.append(key);
		b.append(": ");
		if (value != null)
			b.append('"');
		b.append(value);
		if (value != null)
			b.append('"');
		return b;
	}
}
