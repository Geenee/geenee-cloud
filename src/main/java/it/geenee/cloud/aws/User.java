package it.geenee.cloud.aws;

import javax.xml.bind.annotation.*;


@XmlAccessorType(XmlAccessType.NONE)
public class User {
	@XmlElement(name = "ID")
	public String id;

	@XmlElement(name = "DisplayName")
	public String displayName;
}
