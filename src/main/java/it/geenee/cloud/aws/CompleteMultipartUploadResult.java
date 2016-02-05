package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadComplete.html
 */
@XmlRootElement(name = "CompleteMultipartUploadResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
@XmlAccessorType(XmlAccessType.NONE)
public class CompleteMultipartUploadResult {
	@XmlElement(name = "Location")
	public String location;

	@XmlElement(name = "Bucket")
	public String bucket;

	@XmlElement(name = "Key")
	public String key;

	@XmlElement(name = "ETag")
	public String eTag;
}
