package it.geenee.cloud.aws;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import it.geenee.cloud.*;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import it.geenee.cloud.http.HttpFuture;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;
import org.slf4j.LoggerFactory;


public class AwsComputeTest {
	String region = "eu-central-1";

	@BeforeClass
	public static void setup() {
		// set logging level to INFO
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.INFO);
	}

	@Test
	public void testGetInstance() throws Exception {
		Cloud cloud = new AwsCloud(Cloud.configure().timeout(5));

		try {
			InstanceInfo instanceInfo = cloud.getInstance();
			System.out.println(instanceInfo.instanceId);
			System.out.println(instanceInfo.groupId);
			System.out.println(instanceInfo.zone);
			System.out.println(instanceInfo.region);
			System.out.println(instanceInfo.privateIpAddress);
			System.out.println(instanceInfo.ipAddress);
			Assert.assertFalse(instanceInfo.instanceId.isEmpty());
			Assert.assertFalse(instanceInfo.groupId.isEmpty());
			Assert.assertFalse(instanceInfo.zone.isEmpty());
			Assert.assertFalse(instanceInfo.region.isEmpty());
			Assert.assertFalse(instanceInfo.privateIpAddress.isEmpty());
			Assert.assertFalse(instanceInfo.ipAddress.isEmpty());
		} catch (ExecutionException e) {
			e.getCause().printStackTrace();
			Assert.fail();
		}
	}

	@Test
	public void testInstances() throws Exception {
		Cloud cloud = new AwsCloud(Cloud.configure()
				.region(region)
				//.defaultUser() // first try default user in ~/.aws/credentials
				.instanceRole()); // then try instance role (works if we run on ec2 with assigned role)
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
		Cloud cloud = new AwsCloud(Cloud.configure()
				.region(region)
				//.defaultUser() // first try default user in ~/.aws/credentials
				.instanceRole()); // then try instance role (works if we run on ec2 with assigned role)
		Compute compute = cloud.getCompute();

		List<InstanceInfo> instanceInfos = compute.list().get();
		for (InstanceInfo instanceInfo : instanceInfos) {
			Map<String, String> tags = compute.getTags(instanceInfo.instanceId);
			System.out.println(instanceInfo.instanceId + ": " + tags.toString());
		}
	}
}
