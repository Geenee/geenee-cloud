package it.geenee.cloud.http;

import org.junit.Test;
import org.junit.Assert;


public class HttpCloudTest {

	@Test
	public void testEncodeUrl() throws Exception {
		Assert.assertEquals("some%2Fpath%2Ffoo%20bar", HttpCloud.encodeUrl("some/path/foo bar"));
	}

	@Test
	public void testEncodePath() throws Exception {
		Assert.assertEquals("some/path/foo%20bar", HttpCloud.encodePath("some/path/foo bar"));
	}

	@Test
	public void testAddQuery() throws Exception {
		Assert.assertEquals("some/path?foo=bar", HttpCloud.addQuery("some/path", "foo", "bar"));
		Assert.assertEquals("some/path?query&foo=bar", HttpCloud.addQuery("some/path?query", "foo", "bar"));

		Assert.assertEquals("some/path?foo=5", HttpCloud.addQuery("some/path", "foo", "5"));
		Assert.assertEquals("some/path?query&foo=5", HttpCloud.addQuery("some/path?query", "foo", "5"));

		Assert.assertEquals("some/path?foo", HttpCloud.addQuery("some/path", "foo"));
		Assert.assertEquals("some/path?query&foo", HttpCloud.addQuery("some/path?query", "foo"));
	}
}