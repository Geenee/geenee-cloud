package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGET.html
 */
@XmlRootElement(name = "ListBucketResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
@XmlAccessorType(XmlAccessType.NONE)
public class ListBucketResult {
	@XmlElement(name = "Name")
	public String bucket;

	@XmlElement(name = "Prefix")
	public String prefix;

	@XmlElement(name = "Marker")
	public String marker;

	@XmlElement(name = "MaxKeys")
	public int maxKeys;

	@XmlElement(name = "IsTruncated")
	public boolean isTruncated;

	public static class Owner {
		@XmlElement(name = "ID")
		public String id;
	}

	public static class Entry {
		@XmlElement(name = "Key")
		public String key;

		@XmlElement(name = "LastModified")
		public String lastModified;

		@XmlElement(name = "ETag")
		public String eTag;

		@XmlElement(name = "Size")
		public long size;

		@XmlElement(name = "Owner")
		public Owner owner;

		@XmlElement(name = "StorageClass")
		public String storageClass;
	}
	
	@XmlElement(name = "Contents")
	public List<Entry> contents;
}
