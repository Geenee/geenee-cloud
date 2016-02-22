package it.geenee.cloud.aws;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;
import java.util.Random;

import it.geenee.cloud.*;

import org.junit.Test;
import org.junit.Assert;


public class AwsStorageTest {
	Credentials credentials = AwsCloud.getCredentialsFromFile("efreet-upload-testing");
	String region = "eu-central-1";
	String bucket = "efreet-recognition-testing";

	File smallFilePath = new File("small test file");
	int smallFileSize = 1000000;
	File largeFilePath = new File("large test file");
	int largeFileSize = (AwsCloud.DEFAULT_CONFIGURATION.partSize * 3) / 2; // 2.5 parts

	Storage storage;
	Storage prefixedStorage;


	public AwsStorageTest() throws IOException {

		// create cloud storage object
		Configuration configuration = Cloud.configure()
				.region(region)
				.credentials(credentials)
				.build();
		Cloud cloud = new AwsCloud(configuration);
		this.storage = cloud.getStorage();
		this.prefixedStorage = cloud.getStorage(Cloud.configure().prefix(bucket).build());
	}

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
					System.out.println("SUCCESS hash: " + transfer.getHash() + " version: " + transfer.getVersion());
					break loop;
				case FAILED:
					System.out.println("FAILED");
					break loop;
			}

			transfer.waitForStateChange();
		}
	}

	public void testUpload(File path, String remotePath) throws Exception {
		try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
			Transfer uploader = this.storage.startUpload(file.getChannel(), remotePath);

			wait(uploader);

			// compare hash of local file with the hash (etag) returned by aws
			Assert.assertEquals(uploader.getHash(), this.storage.calculateHash(file.getChannel()));
		}
	}

	public void testDownload(File path, String remotePath) throws Exception {
		try (RandomAccessFile file = new RandomAccessFile(path, "rw")) {
			Transfer downloader = this.storage.startDownload(file.getChannel(), remotePath, null);

			wait(downloader);

			// compare hash of local file with the hash (etag) returned by aws
			Assert.assertEquals(downloader.getHash(), this.storage.calculateHash(file.getChannel()));
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
		this.storage.deleteFile(remotePath, null);

		// check if remote file is gone
		Assert.assertTrue(this.storage.getFiles(remotePath).isEmpty());
	}

	@Test
	public void testTransfer() throws Exception {
		testTransfer(smallFilePath, smallFileSize);
		testTransfer(largeFilePath, largeFileSize);
	}

	@Test
	public void testGetAndDeleteUploads() throws Exception {
		// get list of incomplete uploads
		List<UploadInfo> uploadInfos = this.storage.getUploads(bucket);

		for (UploadInfo uploadInfo : uploadInfos) {
			System.out.println(uploadInfo.toString());

			// delete incomplete upload
			this.storage.deleteUpload(bucket + uploadInfo.path, uploadInfo.uploadId);
		}
	}

	@Test
	public void testGetFiles() throws Exception {
		Map<String, String> fileMap = this.storage.getFiles(bucket);
		System.out.println(fileMap);

		List<FileInfo> fileInfos = this.storage.getFiles(bucket, Storage.ListMode.VERSIONED_DELETED_ALL);
		List<FileInfo> prefixedFileInfos = this.prefixedStorage.getFiles("", Storage.ListMode.VERSIONED_DELETED_ALL);

		for (FileInfo fileInfo : fileInfos) {
			System.out.println(fileInfo.toString());
		}

		int size = fileInfos.size();
		if (size == prefixedFileInfos.size()) {
			for (int i = 0; i < size; ++i) {
				Assert.assertEquals(fileInfos.get(i).path, fileInfos.get(i).path);
			}
		} else {
			// the two file lists are different
			Assert.fail();
		}
	}
}
