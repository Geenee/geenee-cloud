package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * http://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeTags.html
 */
@XmlRootElement(name = "DescribeTagsResponse", namespace = "http://ec2.amazonaws.com/doc/2015-10-01/")
@XmlAccessorType(XmlAccessType.NONE)
public class DescribeTagsResponse {
	@XmlElement(name = "requestId")
	public String requestId;

	public static class Item {
		@XmlElement(name = "resourceId")
		public String resourceId;

		@XmlElement(name = "resourceType")
		public String resourceType;

		@XmlElement(name = "key")
		public String key;

		@XmlElement(name = "value")
		public String value;
	}

	public static class TagSet {
		@XmlElement(name = "item")
		public List<Item> items;
	}

	@XmlElement(name = "tagSet")
	public TagSet tagSet;
}
