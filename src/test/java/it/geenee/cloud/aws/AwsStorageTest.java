package it.geenee.cloud.aws;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;
import java.util.Random;

import it.geenee.cloud.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;


public class AwsStorageTest {
	//Credentials credentials = AwsCloud.getCredentialsFromFile("efreet-upload-testing");
	Credentials credentials = AwsCloud.getCredentialsFromFile("efreet-upload-production");
	String region = "eu-central-1";
	//String bucket = "efreet-recognition-testing";
	String bucket = "efreet-recognition-production";

	File smallFilePath = new File("smallTestFile");
	int smallFileSize = 100000;
	File largeFilePath = new File("largeTestFile");
	int largeFileSize = 20000000;

	void generateFile(File path, int size) throws IOException {
		Random random = new Random(1337);
		byte[] data = new byte[1000];
		try (FileOutputStream f = new FileOutputStream(path)) {
			for (int i = 0; i < size / 1000; ++i) {
				random.nextBytes(data);
				f.write(data);
			}
		}
	}

	@Before
	public void generateFiles() throws IOException {
		generateFile(smallFilePath, smallFileSize);
		generateFile(largeFilePath, largeFileSize);
	}

	public void wait(Transfer transfer) throws InterruptedException {
		System.out.println("START url: " + transfer.getUrl());
		while (transfer.getState() == Transfer.State.INITIATING)
			transfer.waitForStateChange();

		loop:
		while (true) {

			StringBuilder b = new StringBuilder();
			for (int i = 0; i < transfer.getPartCount(); ++i) {
				Transfer.Part part = transfer.getPart(i);
				switch (part.getState()) {
					case QUEUED:
						b.append('.');
						break;
					case INITIATING:
						b.append('i');
						break;
					case RETRY:
						b.append((char)('0' + part.getRetryCount()));
						break;
					case PROGRESS:
						b.append('p');
						break;
					case DONE:
						b.append('D');
						break;
					case FAILED:
						b.append('X');
						break;
				}
			}
			System.out.println(b);

			Transfer.State state = transfer.getState();
			switch (state) {
				case SUCCESS:
					System.out.println("SUCCESS hash: " + transfer.getHash() + " version: " + transfer.getVersion());
					break loop;
				case FAILED:
					System.out.println("FAILED");
					break loop;
			}

			transfer.waitForStateChange();
		}
	}

	public void testDownload(File path) throws Exception {

		Configuration configuration = Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build();
		Cloud cloud = new AwsCloud(configuration);
		Storage storage = cloud.getStorage();

		try (RandomAccessFile file = new RandomAccessFile(path, "rw")) {
			Transfer downloader = storage.startDownload(file.getChannel(), bucket + "/" + path.getName(), null);

			wait(downloader);

			// compare hash of local file with the hash (etag) returned by aws
			Assert.assertEquals(downloader.getHash(), storage.calculateHash(file.getChannel()));
		}
	}

	public void testUpload(File path) throws Exception {

		Cloud cloud = new AwsCloud();
		Configuration configuration = Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build();
		Storage storage = cloud.getStorage(configuration);

		try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
			Transfer uploader = storage.startUpload(file.getChannel(), bucket + "/" + path.getName());

			wait(uploader);

			// compare hash of local file with the hash (etag) returned by aws
			Assert.assertEquals(uploader.getHash(), storage.calculateHash(file.getChannel()));
		}
	}

	@Test
	public void testTransfer() throws Exception {
		testUpload(smallFilePath);
		testUpload(largeFilePath);

		smallFilePath.delete();
		largeFilePath.delete();

		testDownload(smallFilePath);
		testDownload(largeFilePath);
	}

	@Test
	public void testGetUploads() throws Exception {
		Cloud cloud = new AwsCloud(Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build());

		Storage storage = cloud.getStorage();
		List<UploadInfo> uploadInfos = storage.getUploads(bucket);

		System.out.println(uploadInfos.toString());
	}

	@Test
	public void testGetList() throws Exception {
		Cloud cloud = new AwsCloud(Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build());

		Storage storage = cloud.getStorage();
		Map<String, String> fileMap = storage.getFiles(bucket);
		List<FileInfo> fileInfos = storage.getFiles(bucket, Storage.ListMode.VERSIONED_DELETED_ALL);

		System.out.println(fileInfos.toString());
	}
}
