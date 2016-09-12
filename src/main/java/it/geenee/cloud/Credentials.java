package it.geenee.cloud;

public class Credentials {
	public final String accessKey;
	public final String secretAccessKey;
	public final String token;

	public Credentials(String accessKey, String secretAccessKey) {
		this.accessKey = accessKey;
		this.secretAccessKey = secretAccessKey;
		this.token = null;
	}

	public Credentials(String accessKey, String secretAccessKey, String token) {
		this.accessKey = accessKey;
		this.secretAccessKey = secretAccessKey;
		this.token = token;
	}
}
