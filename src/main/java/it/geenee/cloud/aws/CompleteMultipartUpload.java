package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadComplete.html
 */
@XmlRootElement(name = "CompleteMultipartUpload")
@XmlAccessorType(XmlAccessType.NONE)
public class CompleteMultipartUpload {

	public static class Part {
		@XmlElement(name = "PartNumber")
		public int partNumber;

		@XmlElement(name = "ETag")
		public String eTag;
	}
	@XmlElement(name = "Part")
	public List<Part> parts = new ArrayList<>();

	public void addPart(int partNumber, String eTag) {
		Part part = new Part();
		part.partNumber = partNumber;
		part.eTag = eTag;
		this.parts.add(part);
	}
}
