package it.geenee.cloud.aws;

import it.geenee.cloud.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.Assert;


public class AwsComputeTest {
	Credentials credentials = AwsCloud.getCredentialsFromFile("efreet-server-testing");
	String region = "us-east-1";
	String instanceId = "i-0e57209551c14c5e5";

	@Test
	public void testGetInstance() throws Exception {
		//HttpQuery.HTTP_PORT = 8080;
		//AwsGetInstance.HOST = "localhost";

		Cloud cloud = new AwsCloud(Cloud.configure().timeout(1).build());

		try {
			Instance instance = cloud.getInstance().get();
			System.out.println(instance.id);
			System.out.println(instance.zone);
			System.out.println(instance.region);
			Assert.assertFalse(instance.id.isEmpty());
			Assert.assertFalse(instance.zone.isEmpty());
			Assert.assertFalse(instance.region.isEmpty());
		} catch (ExecutionException e) {
			e.getCause().printStackTrace();
			Assert.fail();
		}
	}

	@Test
	public void testTags() throws Exception {
		Configuration configuration = Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build();
		Cloud cloud = new AwsCloud(configuration);
		Compute compute = cloud.getCompute();

		Map<String, String> tags = compute.getTags(instanceId).get();

		System.out.println(tags.toString());
	}
}
