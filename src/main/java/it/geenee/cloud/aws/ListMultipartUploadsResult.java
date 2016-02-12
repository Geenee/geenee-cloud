package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadListMPUpload.html
 */
@XmlRootElement(name = "ListMultipartUploadsResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
@XmlAccessorType(XmlAccessType.NONE)
public class ListMultipartUploadsResult {
	@XmlElement(name = "Bucket")
	public String bucket;

	@XmlElement(name = "KeyMarker")
	public String keyMarker;

	@XmlElement(name = "UploadIdMarker")
	public String uploadIdMarker;

	@XmlElement(name = "NextKeyMarker")
	public String nextKeyMarker;

	@XmlElement(name = "NextUploadIdMarker")
	public String nextUploadIdMarker;

	@XmlElement(name = "MaxUploads")
	public int maxUploads;

	@XmlElement(name = "IsTruncated")
	public boolean isTruncated;

	public static class Upload {

		@XmlElement(name = "Key")
		public String key;

		@XmlElement(name = "UploadId")
		public String uploadId;

		@XmlElement(name = "Initiator")
		public User initiator;

		@XmlElement(name = "Owner")
		public User owner;

		@XmlElement(name = "StorageClass")
		public String storageClass;

		@XmlElement(name = "Initiated")
		public String initiated;
	}

	@XmlElement(name = "Upload")
	public List<Upload> uploads;
}
