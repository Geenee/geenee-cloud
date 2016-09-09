package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * AWS error response
 * http://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html
 */
@XmlRootElement(name = "Error")
@XmlAccessorType(XmlAccessType.NONE)
public class S3Error {
	@XmlElement(name = "RequestId")
	public String requestId;

	@XmlElement(name = "Code")
	public String code;

	@XmlElement(name = "Message")
	public String message;
}
