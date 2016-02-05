package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadInitiate.html
 */
@XmlRootElement(name = "InitiateMultipartUploadResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
@XmlAccessorType(XmlAccessType.NONE)
public class InitiateMultipartUploadResult {
	@XmlElement(name = "Bucket")
	public String bucket;

	@XmlElement(name = "Key")
	public String key;

	@XmlElement(name = "UploadId")
	public String uploadId;
}
