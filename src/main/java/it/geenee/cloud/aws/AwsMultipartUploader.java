package it.geenee.cloud.aws;

import java.io.*;
import java.nio.channels.FileChannel;

import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.*;

import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpTransfer;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * AWS mulitpart uploader
 */
public class AwsMultipartUploader extends HttpTransfer {
	String remotePath;
	long size;

	// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadInitiate.html
	class InitiateHandler extends HttpTransfer.RequestHandler {
		@Override
		protected FullHttpRequest getRequest() throws Exception {
			return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, urlPath + "?uploads");
		}

		@Override
		protected void success(FullHttpResponse response) throws Exception {
			// parse xml
			JAXBContext jc = JAXBContext.newInstance(InitiateMultipartUploadResult.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			InitiateMultipartUploadResult result = (InitiateMultipartUploadResult) unmarshaller.unmarshal(new ByteBufInputStream(response.content()));

			// initate done, start upload
			startTransfer(size, result.uploadId);
		}
	}

	// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadComplete.html
	class CompleteHandler extends HttpTransfer.RequestHandler {
		CompleteMultipartUpload completeMultipartUpload;

		CompleteHandler(CompleteMultipartUpload completeMultipartUpload) {
			this.completeMultipartUpload = completeMultipartUpload;
		}

		@Override
		protected FullHttpRequest getRequest() throws Exception {
			FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, urlPath + "?uploadId=" + id);

			// set CompleteMultipartUpload as content
			JAXBContext jc = JAXBContext.newInstance(CompleteMultipartUpload.class);
			Marshaller marshaller = jc.createMarshaller();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			marshaller.marshal(completeMultipartUpload, os);
			request.content().writeBytes(os.toByteArray());

			return request;
		}

		@Override
		protected void success(FullHttpResponse response) throws Exception {

			// parse xml
			JAXBContext jc = JAXBContext.newInstance(CompleteMultipartUploadResult.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			CompleteMultipartUploadResult result = (CompleteMultipartUploadResult) unmarshaller.unmarshal(new ByteBufInputStream(response.content()));

			AwsMultipartUploader parent = AwsMultipartUploader.this;

			// FIXME timestamp
			String hash = AwsCloud.getHash(result.eTag);
			long timestamp = 0;
			String version = parent.cloud.getVersion(response.headers());

			synchronized (parent) {
				parent.fileInfo = new FileInfo(parent.remotePath, hash, parent.size, timestamp, version, true);
			}

			// upload completed successfully
			setSuccess(parent.fileInfo);
		}
	}

	public AwsMultipartUploader(HttpCloud cloud, Configuration configuration, FileChannel file, String host, String urlPath, String remotePath, long size) {
		super(cloud, configuration, file, host, urlPath);
		this.remotePath = remotePath;
		this.size = size;

		// connect to host
		connect(new InitiateHandler());
	}

	// helpers

	@Override
	protected void connect(Part part) {
		// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadUploadPart.html
		connect(new UploadHandler(urlPath + "?partNumber=" + (part.index + 1) + "&uploadId=" + id, part) {
			@Override
			protected void success(Part part, HttpHeaders headers) {
				// set state of part to SUCCESS (ETag is id of uploaded part)
				part.success(headers.get("ETag"));
			}
		});
	}

	@Override
	protected void completeTransfer() {
		// all parts are done, but we need a completion step
		setState(State.COMPLETING);

		// complete multipart upload
		CompleteMultipartUpload completeMultipartUpload = new CompleteMultipartUpload();
		for (Part part : this.parts) {
			completeMultipartUpload.addPart(part.index + 1, part.id);
		}
		connect(new CompleteHandler(completeMultipartUpload));
	}
}
