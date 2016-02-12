package it.geenee.cloud.aws;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpException;
import it.geenee.cloud.http.HttpFuture;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

/**
 * AWS list multipart uploads
 * http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadListMPUpload.html
 */
public class AwsListMultipartUploads extends HttpFuture<List<Upload>> {

	class ListHandler extends HttpFuture.GetHandler {
		int prefixLength;

		ListHandler(String uri, int prefixLength) {
			super(uri);
			this.prefixLength = prefixLength;
		}

		@Override
		protected void success(byte[] content) throws Exception {
			// parse xml
			System.out.println(new String(content, HttpCloud.UTF_8));
			JAXBContext jc = JAXBContext.newInstance(ListMultipartUploadsResult.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			ListMultipartUploadsResult response = (ListMultipartUploadsResult) unmarshaller.unmarshal(new ByteArrayInputStream(content));

			List<Upload> list = new ArrayList<>();
			for (ListMultipartUploadsResult.Upload upload : response.uploads) {
				list.add(new Upload(
						upload.key.substring(this.prefixLength),
						upload.uploadId
				));
			}

			setSuccess(list);
		}
	}

	public AwsListMultipartUploads(HttpCloud cloud, String host, String remotePath, Configuration configuration) {
		super(cloud, host, configuration, true);

		int pos = remotePath.indexOf('/');
		if (pos == -1)
			pos = remotePath.length();
		String uri = '/' + HttpCloud.encode(remotePath.substring(0, pos)) + "?uploads";
		int prefixLength = 0;
		if (pos < remotePath.length()-1) {
			uri += "&prefix=" + HttpCloud.encode(remotePath.substring(pos + 1));
			prefixLength = remotePath.length() - (pos + 1);
		}
		connect(new ListHandler(uri, prefixLength));
	}
}
