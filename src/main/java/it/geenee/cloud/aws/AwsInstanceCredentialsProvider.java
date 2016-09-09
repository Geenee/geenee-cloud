package it.geenee.cloud.aws;

import java.util.concurrent.TimeUnit;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.geenee.cloud.*;

import it.geenee.cloud.http.HttpCloud;

/**
 * http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html#instance-metadata-security-credentials
 * http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html
 */
public class AwsInstanceCredentialsProvider implements Cloud.CredentialsProvider {
	static Logger logger = LoggerFactory.getLogger(AwsInstanceCredentialsProvider.class);

	final HttpCloud.Globals globals;
	String role;
	Credentials credentials;

	public AwsInstanceCredentialsProvider(HttpCloud.Globals globals) throws Exception {
		this.globals = globals;

		// get role name
		// curl http://instance-data/latest/meta-data/iam/info
		this.role = (new AwsGetMetadata<String>(globals, "/latest/meta-data/iam/info") {

			@Override
			protected void done(String path, String value) {
				// value is contents of http://instance-data/latest/meta-data/iam/info

				Gson gson = new GsonBuilder().create();
				Info info = gson.fromJson(value, Info.class);

				// get role
				int roleStart = info.InstanceProfileArn.indexOf('/') + 1;
				String role =  info.InstanceProfileArn.substring(roleStart);
				setSuccess(role);
			}
		}).get();

		// get credentials
		this.credentials = startGetCredentials().get();

		// refresh credentials periodically
		setTimeout(globals.timer);

		logger.info("using instance credentials for role '{}'", this.role);
	}

	public void setTimeout(HashedWheelTimer timer) {
		timer.newTimeout((timeout) -> {
			AwsGetMetadata<Credentials> future = startGetCredentials();
			future.addListener(f -> {
				if (f.isSuccess()) {
					setCredentials(future.getNow());
					logger.info("refreshed instance credentials for role '{}'", this.role);
				} else {
					// failed
					logger.error("unable to get instance credentials");
				}
			});
			setTimeout(timer);
		}, 10 * 60, TimeUnit.SECONDS);
	}

	@Override
	public synchronized Credentials getCredentials() {
		return this.credentials;
	}

	// helpers

	synchronized void setCredentials(Credentials credentials) {
		this.credentials = credentials;
	}

	AwsGetMetadata<Credentials> startGetCredentials() {
		// get instance credentials
		// curl http://instance-data/latest/meta-data/iam/security-credentials/<role>
		return new AwsGetMetadata<Credentials>(this.globals, "/latest/meta-data/iam/security-credentials/" + this.role) {

			@Override
			protected void done(String path, String value) {
				// value is contents of http://instance-data/latest/meta-data/iam/info

				Gson gson = new GsonBuilder().create();
				SecurityCredentials securityCredentials = gson.fromJson(value, SecurityCredentials.class);

				// get credentials
				Credentials credentials = new Credentials(
						securityCredentials.AccessKeyId,
						securityCredentials.SecretAccessKey,
						securityCredentials.Token
				);
				setSuccess(credentials);
			}
		};
	}


	static class Info {
		String Code;
		String LastUpdated;
		String InstanceProfileArn;
		String InstanceProfileId;
	}

	static class SecurityCredentials {
		String Code;
		String LastUpdated;
		String Type;
		String AccessKeyId;
		String SecretAccessKey;
		String Token;
		String Expiration;
	}
}
