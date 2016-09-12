package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * AWS error response
 * http://docs.aws.amazon.com/AWSEC2/latest/APIReference/errors-overview.html
 */
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.NONE)
public class Ec2Error {
	@XmlElement(name = "RequestID")
	public String requestId;

	public static class Errors {
		@XmlElement(name = "Error")
		public List<Error> errors;
	}

	public static class Error {
		@XmlElement(name = "Code")
		public String code;

		@XmlElement(name = "Message")
		public String message;
	}

	@XmlElement(name = "Errors")
	public Errors errors;
}
