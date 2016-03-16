package it.geenee.cloud.aws;

import it.geenee.cloud.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import it.geenee.cloud.http.HttpFuture;
import org.junit.Test;
import org.junit.Assert;


public class AwsComputeTest {
	Credentials credentials = AwsCloud.getCredentialsFromFile("efreet-server-testing");
	String region = "eu-central-1";

	//@Test
	public void testGetInstance() throws Exception {
		//HttpFuture.HTTP_PORT = 8080;
		//AwsGetInstance.HOST = "localhost";

		Cloud cloud = new AwsCloud(Cloud.configure().timeout(5).build());

		try {
			InstanceInfo instanceInfo = cloud.getInstance();
			System.out.println(instanceInfo.instanceId);
			System.out.println(instanceInfo.zone);
			System.out.println(instanceInfo.region);
			Assert.assertFalse(instanceInfo.instanceId.isEmpty());
			Assert.assertFalse(instanceInfo.zone.isEmpty());
			Assert.assertFalse(instanceInfo.region.isEmpty());
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

		List<InstanceInfo> instanceInfos;

		instanceInfos = compute.list().filterTagKey("Role").filterTagValue("master", "foo bar").filterTag("Group", "production").get();
		Assert.assertEquals(1, instanceInfos.size());
		System.out.println(instanceInfos.toString());

		instanceInfos = compute.list().filterTag("Role", "master").filterTag("Group", "production").get();
		Assert.assertEquals(1, instanceInfos.size());
		System.out.println(instanceInfos.toString());
	}

	@Test
	public void testTags() throws Exception {
		Configuration configuration = Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build();
		Cloud cloud = new AwsCloud(configuration);
		Compute compute = cloud.getCompute();

		List<InstanceInfo> instanceInfos = compute.list().get();
		for (InstanceInfo instanceInfo : instanceInfos) {
			Map<String, String> tags = compute.getTags(instanceInfo.instanceId);
			System.out.println(instanceInfo.instanceId + ": " + tags.toString());
		}
	}
}
