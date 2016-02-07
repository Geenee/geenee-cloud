package it.geenee.cloud.aws;

import it.geenee.cloud.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.Assert;


public class AwsComputeTest {
	Credentials credentials = AwsCloud.getCredentialsFromFile("efreet-server-testing");
	String region = "us-east-1";
	String instanceId = "i-0e57209551c14c5e5";

	//@Test
	public void testGetInstance() throws Exception {
		//HttpFuture.HTTP_PORT = 8080;
		//AwsGetInstance.HOST = "localhost";

		Cloud cloud = new AwsCloud(Cloud.configure().timeout(1).build());

		try {
			Instance instance = cloud.getInstance();
			System.out.println(instance.instanceId);
			System.out.println(instance.zone);
			System.out.println(instance.region);
			Assert.assertFalse(instance.instanceId.isEmpty());
			Assert.assertFalse(instance.zone.isEmpty());
			Assert.assertFalse(instance.region.isEmpty());
		} catch (ExecutionException e) {
			e.getCause().printStackTrace();
			Assert.fail();
		}
	}

	@Test
	public void testInstances() throws Exception {
		Configuration configuration = Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build();
		Cloud cloud = new AwsCloud(configuration);
		Compute compute = cloud.getCompute();

		List<Instance> instances;

		instances = compute.instances().filterTagKey("Role").filterTagValue("efreet-master", "foo bar").get();
		Assert.assertEquals(1, instances.size());
		System.out.println(instances.toString());

		instances = compute.instances().filterTag("Role", "efreet-master").get();
		Assert.assertEquals(1, instances.size());
		System.out.println(instances.toString());
	}

	//@Test
	public void testTags() throws Exception {
		Configuration configuration = Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build();
		Cloud cloud = new AwsCloud(configuration);
		Compute compute = cloud.getCompute();

		Map<String, String> tags = compute.getTags(instanceId);

		System.out.println(tags.toString());
	}
}
