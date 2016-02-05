package it.geenee.cloud.aws;

import it.geenee.cloud.*;

import java.io.File;
import java.io.RandomAccessFile;


public class AwsStorageTest {
	Credentials credentials = AwsCloud.getCredentialsFromFile("efreet-upload-testing");
	String region = "eu-central-1";
	String bucket = "efreet-recognition-testing";
	String largeFileName = "largeFile"; // should be at least 20MB
	String smallFileName = "smallFile"; // should be 0k - 100k


	public void wait(Transfer transfer) throws InterruptedException {
		System.out.println("START url: " + transfer.getUrl());
		while (transfer.getState() == Transfer.State.INITIATING)
			transfer.waitForStateChange();//Thread.sleep(100);

		//downloader.sync();
		loop:
		for (int t = 0; t < 10000000; ++t) {

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
			String s = b.toString();
			System.out.printf("%4d %s\n", t, s);

			Transfer.State state = transfer.getState();
			switch (state) {
				case SUCCESS:
					System.out.println("SUCCESS hash: " + transfer.getHash() + " version: " + transfer.getVersion());
					break loop;
				case FAILED:
					System.out.println("FAILED");
					break loop;
			}

			//Thread.sleep(100);
			transfer.waitForStateChange();
		}
	}

	public void testDownload(String fileName) throws Exception {

		Configuration configuration = Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build();
		Cloud cloud = new AwsCloud(configuration);
		Storage storage = cloud.getStorage();

		File path = new File(fileName);
		try (RandomAccessFile file = new RandomAccessFile(path, "rw")) {
			Transfer downloader = storage.download(file.getChannel(), "/" + bucket + "/" + fileName, null);

			wait(downloader);
		}
	}
	//@Test
	public void testSmallDownload() throws Exception {
		testDownload(smallFileName);
	}
	//@Test
	public void testLargeDownload() throws Exception {
		testDownload(largeFileName);
	}


	public void testUpload(String fileName) throws Exception {

		Cloud cloud = new AwsCloud();
		Configuration configuration = Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build();
		Storage storage = cloud.getStorage(configuration);

		String name = "Referrer.key";
		File path = new File(fileName);
		try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
			Transfer uploader = storage.upload(file.getChannel(), "/efreet-recognition-testing/" + name);

			wait(uploader);
		}
	}
	//@Test
	public void testSmallUpload() throws Exception {
		testUpload(smallFileName);
	}
	//@Test
	public void testLargeUpload() throws Exception {
		testUpload(largeFileName);
	}
}
