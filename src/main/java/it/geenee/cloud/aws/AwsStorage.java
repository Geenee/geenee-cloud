package it.geenee.cloud.aws;

import io.netty.handler.codec.http.HttpMethod;
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
		String pathAndQuery = HttpCloud.encodePath('/' + remotePath);
		return new HttpDownloader(this.cloud, this.configuration, file, host, pathAndQuery, version);
	}

	@Override
	public Transfer startUpload(FileChannel file, String remotePath) throws IOException {
		String pathAndQuery = HttpCloud.encodePath('/' + remotePath);
		if (file.size() <= this.configuration.partSize)
			return new AwsUploader(this.cloud, this.configuration, file, host, pathAndQuery);
		return new AwsMultipartUploader(this.cloud, this.configuration, file, host, pathAndQuery);
	}

	@Override
	public Future<Map<String, String>> startGetFiles(final String remotePath) {
		final String pathAndQuery = encodePathPrefix(remotePath);
		final Map<String, String> map = new HashMap<>();
		return new AwsRequest<Map<String, String>>(this.cloud, this.configuration, this.host, HttpMethod.GET, pathAndQuery) {
			@Override
			protected void success(ByteArrayInputStream content) throws Exception {
				// parse xml
				JAXBContext jc = JAXBContext.newInstance(ListBucketResult.class);
				Unmarshaller unmarshaller = jc.createUnmarshaller();
				ListBucketResult result = (ListBucketResult) unmarshaller.unmarshal(content);

				// copy file entries to map
				if (result.contents != null) {
					for (ListBucketResult.Entry entry : result.contents) {
						map.put(getPath(entry.key, remotePath), AwsCloud.getHash(entry.eTag));
					}
				}

				// either repeat or finished
				if (result.isTruncated) {
					String marker = result.contents.get(result.contents.size()-1).key;
					connect(new ListHandler(HttpMethod.GET, HttpCloud.addQuery(pathAndQuery, "marker", marker)));
				} else {
					setSuccess(map);
				}
			}
		};
	}

	@Override
	public Future<List<FileInfo>> startGetFiles(final String remotePath, final ListMode mode) {
		final List<FileInfo> list = new ArrayList<>();
		if (mode == ListMode.UNVERSIONED) {
			// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGET.html
			final String pathAndQuery = encodePathPrefix(remotePath);
			return new AwsRequest<List<FileInfo>>(this.cloud, this.configuration, this.host, HttpMethod.GET, pathAndQuery) {
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
									getPath(entry.key, remotePath),
									AwsCloud.getHash(entry.eTag),
									entry.size,
									entry.lastModified,
									null,
									true));
						}
					}

					// either repeat or finished
					if (result.isTruncated) {
						String marker = result.contents.get(result.contents.size() - 1).key;
						connect(new ListHandler(HttpMethod.GET, HttpCloud.addQuery(pathAndQuery, "marker", marker)));
					} else {
						setSuccess(list);
					}
				}
			};
		} else {
			// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGETVersion.html
			final String pathAndQuery = HttpCloud.addQuery(encodePathPrefix(remotePath), "versions");
			return new AwsRequest<List<FileInfo>>(this.cloud, this.configuration, this.host, HttpMethod.GET, pathAndQuery) {
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
										getPath(version.key, remotePath),
										AwsCloud.getHash(version.eTag),
										version.size,
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
										getPath(deleteMarker.key, remotePath),
										null,
										0,
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
						connect(new ListHandler(HttpMethod.GET, pq));
					} else {
						setSuccess(list);
					}
				}
			};
		}
	}

	@Override
	public Future<List<UploadInfo>> startGetUploads(final String remotePath) {
		// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadListMPUpload.html
		final String pathAndQuery = HttpCloud.addQuery(encodePathPrefix(remotePath), "uploads");
		final List<UploadInfo> list = new ArrayList<>();
		return new AwsRequest<List<UploadInfo>>(this.cloud, this.configuration, this.host, HttpMethod.GET, pathAndQuery) {
			@Override
			protected void success(ByteArrayInputStream content) throws Exception {
				// parse xml
				JAXBContext jc = JAXBContext.newInstance(ListMultipartUploadsResult.class);
				Unmarshaller unmarshaller = jc.createUnmarshaller();
				ListMultipartUploadsResult result = (ListMultipartUploadsResult) unmarshaller.unmarshal(content);

				// copy file entries to list
				if (result.uploads != null) {
					for (ListMultipartUploadsResult.Upload upload : result.uploads) {
						list.add(new UploadInfo(
								getPath(upload.key, remotePath),
								upload.uploadId,
								upload.initiated));
					}
				}

				// either repeat or finished
				if (result.isTruncated) {
					String pq = pathAndQuery;
					pq = HttpCloud.addQuery(pq, "key-marker", result.nextKeyMarker);
					pq = HttpCloud.addQuery(pq, "upload-id-marker", result.nextUploadIdMarker);
					connect(new ListHandler(HttpMethod.GET, pq));
				} else {
					setSuccess(list);
				}

			}
		};
	}

	@Override
	public Future<Void> startDeleteFile(String remotePath, String version) {
		// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectDELETE.html
		String path = encodePath(remotePath);
		if (version != null)
			path = HttpCloud.addQuery(path, "versionId");

		return new AwsRequest<Void>(this.cloud, this.configuration, this.host, HttpMethod.DELETE, path) {
			@Override
			protected void success(ByteArrayInputStream content) throws Exception {
				setSuccess(null);
			}
		};
	}

	@Override
	public Future<Void> startDeleteUpload(String remotePath, String uploadId) {
		// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadAbort.html
		String pathAndQuery = HttpCloud.addQuery(encodePath(remotePath), "uploadId", uploadId);

		return new AwsRequest<Void>(this.cloud, this.configuration, this.host, HttpMethod.DELETE, pathAndQuery) {
			@Override
			protected void success(ByteArrayInputStream content) throws Exception {
				setSuccess(null);
			}
		};
	}

	// helpers

	String encodePath(String remotePath) {
		StringBuilder path = new StringBuilder();
		path.append('/');
		if (this.configuration.prefix != null)
			path.append(this.configuration.prefix);
		path.append(remotePath);
		return HttpCloud.encodePath(path.toString());
	}

	String encodePathPrefix(String remotePath) {
		if (this.configuration.prefix != null)
			remotePath = this.configuration.prefix + remotePath;
		int pos = remotePath.indexOf('/');
		if (pos == -1)
			pos = remotePath.length();
		String pathAndQuery = '/' + HttpCloud.encodePath(remotePath.substring(0, pos));
		if (pos < remotePath.length()-1) {
			String prefix = remotePath.substring(pos + 1);
			pathAndQuery = HttpCloud.addQuery(pathAndQuery, "prefix", prefix);
		}
		return pathAndQuery;
	}

	String getPath(String key, String remotePath) {
		if (this.configuration.prefix != null) {
			int pos = this.configuration.prefix.indexOf('/');
			if (pos != -1)
				return key.substring(this.configuration.prefix.length() - (pos + 1) + remotePath.length());
		}

		int pos = remotePath.indexOf('/');
		if (pos == -1) {
			// remotePath is just a bucket without trailing slash. ensure that remotePath + path is a new valid remotePath
			return '/' + key;
		}
		return key.substring(remotePath.length() - (pos + 1));
	}
}
