package it.geenee.cloud;

/**
 * Instance description
 */
public class Instance {
	public final String id;
	public final String zone;
	public final String region;

	public Instance(String id, String zone, String region) {
		this.id = id;
		this.zone = zone;
		this.region = region;
	}
}
