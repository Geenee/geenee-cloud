package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGETVersion.html
 */
@XmlRootElement(name = "ListVersionsResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
@XmlAccessorType(XmlAccessType.NONE)
public class ListVersionsResult {
	@XmlElement(name = "Name")
	public String bucket;

	@XmlElement(name = "Prefix")
	public String prefix;

	@XmlElement(name = "KeyMarker")
	public String keyMarker;

	@XmlElement(name = "VersionIdMarker")
	public String versionIdMarker;

	@XmlElement(name = "MaxKeys")
	public int maxKeys;

	@XmlElement(name = "IsTruncated")
	public boolean isTruncated;

	public static class Version {
		@XmlElement(name = "Key")
		public String key;

		@XmlElement(name = "VersionId")
		public String versionId;

		@XmlElement(name = "IsLatest")
		public boolean isLatest;

		@XmlElement(name = "LastModified")
		public String lastModified;

		@XmlElement(name = "ETag")
		public String eTag;

		@XmlElement(name = "Size")
		public long size;

		@XmlElement(name = "Owner")
		public User owner;

		@XmlElement(name = "StorageClass")
		public String storageClass;
	}
	
	@XmlElement(name = "Version")
	public List<Version> versions;

	public static class DeleteMarker {
		@XmlElement(name = "Key")
		public String key;

		@XmlElement(name = "VersionId")
		public String versionId;

		@XmlElement(name = "IsLatest")
		public boolean isLatest;

		@XmlElement(name = "LastModified")
		public String lastModified;

		@XmlElement(name = "Owner")
		public User owner;
	}

	@XmlElement(name = "DeleteMarker")
	public List<DeleteMarker> deleteMarkers;
}
