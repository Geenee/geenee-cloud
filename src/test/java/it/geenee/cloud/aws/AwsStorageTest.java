package it.geenee.cloud.aws;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import it.geenee.cloud.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;
import org.slf4j.LoggerFactory;


public class AwsStorageTest {
	String region = "eu-central-1";
	String bucket = "it.geenee.cloud.test";

	File smallFilePath = new File("tmp/small test file");
	int smallFileSize = 1000000; // 1 MB
	File largeFilePath = new File("tmp/large test file");
	int largeFileSize = (AwsCloud.DEFAULT_CONFIGURATION.partSize * 5) / 2; // 2.5 parts

	Storage storage;
	Storage prefixedStorage;

	@BeforeClass
	public static void setup() {
		// set logging level to INFO
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.INFO);
	}

	public AwsStorageTest() throws IOException {

		// create cloud storage object
		Cloud cloud = new AwsCloud(Cloud.configure()
				.region(region)
				.defaultUser() // first try default user in ~/.aws/credentials
				.instanceRole()); // then try instance role (works if we run on ec2 with assigned role)
		this.storage = cloud.getStorage();
		this.prefixedStorage = cloud.getStorage(Cloud.configure().prefix(bucket));
	}

	void generateFile(File path, int size) throws IOException {
		Random random = new Random(1337);
		byte[] data = new byte[1000];
		File dir = path.getParentFile();
		if (dir != null)
			dir.mkdirs();
		try (FileOutputStream f = new FileOutputStream(path)) {
			for (int i = 0; i < size / 1000; ++i) {
				random.nextBytes(data);
				f.write(data);
			}
		}
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
					case SUCCESS:
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
					FileInfo fileInfo = transfer.getInfo();
					System.out.println("SUCCESS hash: " + fileInfo.hash + " version: " + fileInfo.version);
					break loop;
				case FAILED:
					System.out.println("FAILED");
					break loop;
				case CANCELLED:
					System.out.println("CANCELLED");
					break loop;
			}

			transfer.waitForStateChange();
		}
	}

	public void testUpload(File path, String remotePath) throws Exception {
		try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
			Transfer uploader = this.storage.startUpload(file.getChannel(), remotePath);

			wait(uploader);
			FileInfo uploadInfo = uploader.getInfo();

			// compare hash of uploaded local file with the hash (etag) returned by aws
			Assert.assertEquals(this.storage.hash(file.getChannel()), uploadInfo.hash);

			// get file info and compare
			FileInfo fileInfo = this.storage.getInfo(remotePath, null);
			Assert.assertEquals(uploadInfo.hash, fileInfo.hash);
			Assert.assertEquals(file.getChannel().size(), fileInfo.size);
			Assert.assertEquals(uploadInfo.version, fileInfo.version);

			// get file info for version and compare again
			fileInfo = this.storage.getInfo(remotePath, uploadInfo.version);
			Assert.assertEquals(uploadInfo.hash, fileInfo.hash);
			Assert.assertEquals(file.getChannel().size(), fileInfo.size);
			Assert.assertEquals(uploadInfo.version, fileInfo.version);
		}
	}

	public void testDownload(File path, String remotePath) throws Exception {
		try (RandomAccessFile file = new RandomAccessFile(path, "rw")) {
			Transfer downloader = this.storage.startDownload(file.getChannel(), remotePath, null);

			wait(downloader);
			FileInfo downloadInfo = downloader.getInfo();

			// compare hash of downloaded local file with the hash (etag) returned by aws
			Assert.assertEquals(this.storage.hash(file.getChannel()), downloadInfo.hash);
		}
	}

	public void testTransfer(File path, int size) throws Exception {
		String remotePath = bucket + '/' + path.getName();

		// generate local file
		generateFile(path, size);

		// upload file
		testUpload(path, remotePath);

		// delete local file
		path.delete();

		// download file
		testDownload(path, remotePath);

		// delete remote file
		this.storage.delete(remotePath, null);

		// check if remote file is gone
		Assert.assertTrue(this.storage.listHashes(remotePath).isEmpty());
	}

	@Test
	public void testTransfer() throws Exception {
		testTransfer(smallFilePath, smallFileSize);
		testTransfer(largeFilePath, largeFileSize);
	}

	@Test
	public void testGetAndDeleteUploads() throws Exception {
		// get list of incomplete uploads
		List<UploadInfo> uploadInfos = this.storage.listUploads(bucket);

		for (UploadInfo uploadInfo : uploadInfos) {
			System.out.println(uploadInfo.toString());

			// delete incomplete upload
			this.storage.deleteUpload(bucket + uploadInfo.path, uploadInfo.uploadId);
		}
	}

	@Test
	public void testList() throws Exception {

		// compare file listing obtained with different methods
		FileInfo[] fileInfos = this.storage.list(bucket, Storage.ListMode.VERSIONED_LATEST);
		String[] prefixedFileInfos = this.prefixedStorage.list("");
		Map<String, String> fileMap = this.storage.listHashes(bucket);

		for (FileInfo fileInfo : fileInfos) {
			System.out.println(fileInfo.toString());
		}

		int size = fileInfos.length;
		if (size == prefixedFileInfos.length) {
			for (int i = 0; i < size; ++i) {
				String path = fileInfos[i].path;
				Assert.assertEquals(path, prefixedFileInfos[i]);
				Assert.assertEquals(fileInfos[i].hash, fileMap.get(path));
			}
		} else {
			// the two file lists are different
			Assert.fail();
		}
	}

	@Test
	public void testListVersioned() throws Exception {
		// compare versioned file listing obtained with different methods
		FileInfo[] fileInfos = this.storage.list(bucket, Storage.ListMode.VERSIONED_DELETED_ALL);
		FileInfo[] prefixedFileInfos = this.prefixedStorage.list("", Storage.ListMode.VERSIONED_DELETED_ALL);

		for (FileInfo fileInfo : fileInfos) {
			System.out.println(fileInfo.toString());
		}

		int size = fileInfos.length;
		if (size == prefixedFileInfos.length) {
			for (int i = 0; i < size; ++i) {
				Assert.assertEquals(fileInfos[i].path, prefixedFileInfos[i].path);
			}
		} else {
			// the two file lists are different
			Assert.fail();
		}
	}
}
