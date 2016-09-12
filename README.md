# geenee-cloud

Simple, netty based, cloud abstraction. Currently only Amazon AWS S3 upload/download and EC2 instance enumeration by 
tags is supported

## Introduction
geenee-cloud is an abstraction of cloud infrastructure that currently only supports S3 upload/download and EC2 instance 
enumeration. The interface is future based, this means you can start a file transfer in your thread and then decide if
you wait for the completion of the transfer, poll and display the progress from time to time or install a listener that
informs you when the transfer is complete. See src/test for usage examples.

## Features
- Asynchronous future based interface
- Support for completion listeners
- Support for parallel multipart upload and ranged download
- Support for EC2 instance profiles
- Function to calculate etag of local files that can be compared to etag of files on S3
- Support for S3 versioning

## Usage Example
```java

// create cloud object
Cloud cloud = new AwsCloud(Cloud.configure().region("eu-central-1").user("geenee-cloud-test"));

// create storage object with s3 bucket and path as prefix for all operations on this object
Storage storage = cloud.getStorage(Cloud.configure().prefix("it.geenee.cloud.test/some/path"));

// variant a: upload and wait until complete
{
	try (RandomAccessFile file = new RandomAccessFile(localPath, "r")) {
		FileInfo info = storage.upload(file.getChannel(), remotePath);
	}
}

// variant b: upload and set listener to close the file when complete
{
	final RandomAccessFile file = new RandomAccessFile(localPath, "r");
	Transfer transfer = storage.startUpload(file.getChannel(), remotePath);
	transfer.addListener(f -> {
		if (f.isSuccess()) {
			logger.info("upload succeeded");
			FileInfo info = transfer.getNow();
		} else {
			logger.error("upload failed");
		}
		file.close();
	});

	// after a while you can wait on the transfer to complete
	FileInfo info = transfer.get();
}

```
