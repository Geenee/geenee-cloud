package it.geenee.cloud.aws;

import io.netty.util.concurrent.Future;
import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpDownloader;
import org.apache.commons.codec.binary.Hex;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AwsStorage implements Storage {
	final AwsCloud cloud;
	final Configuration configuration;
	final String host;

	AwsStorage(AwsCloud cloud, Configuration configuration, String host) {
		this.cloud = cloud;
		this.configuration = configuration;
		this.host = host;
	}

	/**
	 * Calculates the Amazon S3 ETag of a file. The algorithm uses the multipart upload chunk size. If the file can be
	 * uploaded as one part, the ETag is the MD5 hash. If the file has to be uploaded in multiple parts, the algorithm
	 * is as follows: Calculate the MD5 hash for each part of the file, concatenate the hashes into a single binary
	 * string and calculate the MD5 hash of that result. Then append '-' and the number of parts.
	 * https://stackoverflow.com/questions/12186993/what-is-the-algorithm-to-compute-the-amazon-s3-etag-for-a-file-larger-than-5gb
	 * @param file file to calculate the ETag for
	 * @return the ETag
	 * @throws Exception
	 */
	@Override
	public String calculateHash(FileChannel file) throws Exception {
		long fileLength = file.size();
		long partSize = this.configuration.partSize;
		long partCount = (fileLength + partSize - 1) / partSize;
		if (partCount <= 1) {
			// single part: calc hex encoded md5 of file
			return Hex.encodeHexString(HttpCloud.md5(file, 0, fileLength));
		} else {
			// multipart: calc md5 for each part
			List<byte[]> partMds = new ArrayList<>();
			for (long partIndex = 0; partIndex < partCount; ++partIndex) {
				long offset = partIndex * partSize;
				long length = Math.min(partSize, fileLength - offset);

				// calc md5 of part
				partMds.add(HttpCloud.md5(file, offset, length));
			}

			// calc md5 of md5's of parts
			MessageDigest md = MessageDigest.getInstance("MD5");
			for (byte[] partMd : partMds) {
				md.update(partMd);
			}

			// return hex encoded md5 with part count
			return Hex.encodeHexString(md.digest()) + '-' + Long.toString(partCount);
		}
	}

	@Override
	public Transfer startDownload(FileChannel file, String remotePath, String version) throws IOException {
		return new HttpDownloader(this.cloud, this.configuration, file, host, '/' + remotePath, version);
	}

	@Override
	public Transfer startUpload(FileChannel file, String remotePath) throws IOException {
		if (file.size() <= this.configuration.partSize)
			return new AwsUploader(this.cloud, this.configuration, file, host, '/' + remotePath);
		return new AwsMultipartUploader(this.cloud, this.configuration, file, host, '/' + remotePath);
	}

	@Override
	public Future<Map<String, String>> startGetFiles(String remotePath) {
		final String pathAndQuery = getPathAndQuery(remotePath);
		final int prefixLength = getPrefixLength(remotePath);
		final Map<String, String> map = new HashMap<>();
		return new AwsList<Map<String, String>>(this.cloud, this.configuration, this.host, pathAndQuery) {
			@Override
			protected void success(ByteArrayInputStream content) throws Exception {
				// parse xml
				JAXBContext jc = JAXBContext.newInstance(ListBucketResult.class);
				Unmarshaller unmarshaller = jc.createUnmarshaller();
				ListBucketResult result = (ListBucketResult) unmarshaller.unmarshal(content);

				// copy file entries to map
				if (result.contents != null) {
					for (ListBucketResult.Entry entry : result.contents) {
						map.put(entry.key.substring(prefixLength), entry.eTag);
					}
				}

				// either repeat or finished
				if (result.isTruncated) {
					String marker = result.contents.get(result.contents.size()-1).key;
					connect(new ListHandler(HttpCloud.addQuery(pathAndQuery, "marker", marker)));
				} else {
					setSuccess(map);
				}
			}
		};
	}

	@Override
	public Future<List<FileInfo>> startGetFiles(String remotePath, final ListMode mode) {
		final int prefixLength = getPrefixLength(remotePath);
		final List<FileInfo> list = new ArrayList<>();
		if (mode == ListMode.UNVERSIONED) {
			// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGET.html
			final String pathAndQuery = getPathAndQuery(remotePath);
			return new AwsList<List<FileInfo>>(this.cloud, this.configuration, this.host, pathAndQuery) {
				@Override
				protected void success(ByteArrayInputStream content) throws Exception {
					// parse xml
					JAXBContext jc = JAXBContext.newInstance(ListBucketResult.class);
					Unmarshaller unmarshaller = jc.createUnmarshaller();
					ListBucketResult result = (ListBucketResult) unmarshaller.unmarshal(content);

					// copy file entries to list
					if (result.contents != null) {
						for (ListBucketResult.Entry entry : result.contents) {
							list.add(new FileInfo(
									entry.key.substring(prefixLength),
									entry.eTag,
									entry.lastModified,
									null,
									true));
						}
					}

					// either repeat or finished
					if (result.isTruncated) {
						String marker = result.contents.get(result.contents.size() - 1).key;
						connect(new ListHandler(HttpCloud.addQuery(pathAndQuery, "marker", marker)));
					} else {
						setSuccess(list);
					}
				}
			};
		} else {
			// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGETVersion.html
			final String pathAndQuery = HttpCloud.addQuery(getPathAndQuery(remotePath), "versions");
			return new AwsList<List<FileInfo>>(this.cloud, this.configuration, this.host, pathAndQuery) {
				@Override
				protected void success(ByteArrayInputStream content) throws Exception {
					// parse xml
					JAXBContext jc = JAXBContext.newInstance(ListVersionsResult.class);
					Unmarshaller unmarshaller = jc.createUnmarshaller();
					ListVersionsResult result = (ListVersionsResult) unmarshaller.unmarshal(content);

					// copy file entries to list
					if (result.versions != null) {
						for (ListVersionsResult.Version version : result.versions) {
							if (version.isLatest || mode == ListMode.VERSIONED_ALL || mode == ListMode.VERSIONED_DELETED_ALL) {
								list.add(new FileInfo(
										version.key.substring(prefixLength),
										version.eTag,
										version.lastModified,
										version.versionId,
										version.isLatest));
							}
						}
					}
					if (result.deleteMarkers != null && (mode == ListMode.VERSIONED_DELETEED_LATEST || mode == ListMode.VERSIONED_DELETED_ALL)) {
						for (ListVersionsResult.DeleteMarker deleteMarker : result.deleteMarkers) {
							if (deleteMarker.isLatest || mode == ListMode.VERSIONED_DELETED_ALL) {
								list.add(new FileInfo(
										deleteMarker.key.substring(prefixLength),
										null,
										deleteMarker.lastModified,
										deleteMarker.versionId,
										deleteMarker.isLatest));
							}
						}
					}

					// either repeat or finished
					if (result.isTruncated) {
						String pq = pathAndQuery;
						pq = HttpCloud.addQuery(pq, "key-marker", result.keyMarker);
						pq = HttpCloud.addQuery(pq, "version-id-marker", result.versionIdMarker);
						connect(new ListHandler(pq));
					} else {
						setSuccess(list);
					}
				}
			};
		}
	}

	@Override
	public Future<List<UploadInfo>> startGetUploads(String remotePath) {
		final String pathAndQuery = HttpCloud.addQuery(getPathAndQuery(remotePath), "uploads");
		final int prefixLength = getPrefixLength(remotePath);
		final List<UploadInfo> list = new ArrayList<>();
		return new AwsList<List<UploadInfo>>(this.cloud, this.configuration, this.host, pathAndQuery) {
			@Override
			protected void success(ByteArrayInputStream content) throws Exception {
				// parse xml
				JAXBContext jc = JAXBContext.newInstance(ListMultipartUploadsResult.class);
				Unmarshaller unmarshaller = jc.createUnmarshaller();
				ListMultipartUploadsResult result = (ListMultipartUploadsResult) unmarshaller.unmarshal(content);

				// copy file entries to list
				for (ListMultipartUploadsResult.Upload upload : result.uploads) {
					list.add(new UploadInfo(
							upload.key.substring(prefixLength),
							upload.uploadId,
							upload.initiated));
				}

				// either repeat or finished
				if (result.isTruncated) {
					String pq = pathAndQuery;
					pq = HttpCloud.addQuery(pq, "key-marker", result.nextKeyMarker);
					pq = HttpCloud.addQuery(pq, "upload-id-marker", result.nextUploadIdMarker);
					connect(new ListHandler(pq));
				} else {
					setSuccess(list);
				}

			}
		};
	}

	@Override
	public Future<Void> startDeleteFile(String remotePath) {
		return null;
	}

	@Override
	public Future<Void> startDeleteUpload(String remotePath) {
		return null;
	}

	// helpers

	String getPathAndQuery(String remotePath) {
		int pos = remotePath.indexOf('/');
		if (pos == -1)
			pos = remotePath.length();
		String pathAndQuery = '/' + HttpCloud.encode(remotePath.substring(0, pos));
		if (pos < remotePath.length()-1) {
			String prefix = remotePath.substring(pos + 1);
			pathAndQuery = HttpCloud.addQuery(pathAndQuery, "prefix", prefix);
		}
		return pathAndQuery;
	}

	int getPrefixLength(String remotePath) {
		int pos = remotePath.indexOf('/');
		if (pos == -1)
			return 0;
		return remotePath.length() - (pos + 1);
	}
}
