package it.geenee.cloud.aws;

import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import it.geenee.cloud.*;
import it.geenee.cloud.http.HttpCloud;
import it.geenee.cloud.http.HttpDownloader;
import org.apache.commons.codec.binary.Hex;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


public class AwsStorage implements Storage {
	final AwsCloud cloud;
	final Configuration configuration;
	final String host;
	static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

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
	public String hash(FileChannel file) throws Exception {
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
	public String getUrl(String remotePath) {
		return "https://" + this.host + HttpCloud.encodePath('/' + this.configuration.prefix + remotePath);
	}

	@Override
	public Transfer startDownload(FileChannel file, String remotePath, String version) {
		String urlPath = HttpCloud.encodePath('/' + this.configuration.prefix + remotePath);
		return new HttpDownloader(this.cloud, this.configuration, file, this.host, urlPath, version);
	}

	@Override
	public Transfer startUpload(FileChannel file, String remotePath) {
		String urlPath = HttpCloud.encodePath('/' + this.configuration.prefix + remotePath);
		return AwsUploader.create(this.cloud, this.configuration, file, this.host, urlPath);
	}

	@Override
	public Future<FileInfo> startGetInfo(String remotePath, String version) {
		return new AwsGetFileInfo(this.cloud, this.configuration, this.host, remotePath, version);
	}

	@Override
	public Future<Map<String, String>> startList(final String remotePath) {
		final String urlPath = encodePathPrefix(remotePath);
		final Map<String, String> map = new HashMap<>();
		return new AwsRequest<Map<String, String>>(this.cloud, this.configuration, this.host, HttpMethod.GET, urlPath) {
			@Override
			protected void success(InputStream content) throws Exception {
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
					connect(new ListHandler(HttpMethod.GET, HttpCloud.addQuery(urlPath, "marker", marker)));
				} else {
					setSuccess(map);
				}
			}
		};
	}

	@Override
	public Future<List<FileInfo>> startList(final String remotePath, final ListMode mode) {
		final List<FileInfo> list = new ArrayList<>();
		if (mode == ListMode.UNVERSIONED) {
			// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGET.html
			final String urlPath = encodePathPrefix(remotePath);
			return new AwsRequest<List<FileInfo>>(this.cloud, this.configuration, this.host, HttpMethod.GET, urlPath) {
				@Override
				protected void success(InputStream content) throws Exception {
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
									DATE_FORMAT.parse(entry.lastModified),
									null,
									true));
						}
					}

					// either repeat or finished
					if (result.isTruncated) {
						String marker = result.contents.get(result.contents.size() - 1).key;
						connect(new ListHandler(HttpMethod.GET, HttpCloud.addQuery(urlPath, "marker", marker)));
					} else {
						setSuccess(list);
					}
				}
			};
		} else {
			// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGETVersion.html
			final String urlPath = HttpCloud.addQuery(encodePathPrefix(remotePath), "versions");
			return new AwsRequest<List<FileInfo>>(this.cloud, this.configuration, this.host, HttpMethod.GET, urlPath) {
				@Override
				protected void success(InputStream content) throws Exception {
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
										DATE_FORMAT.parse(version.lastModified),
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
										DATE_FORMAT.parse(deleteMarker.lastModified),
										deleteMarker.versionId,
										deleteMarker.isLatest));
							}
						}
					}

					// either repeat or finished
					if (result.isTruncated) {
						String p = urlPath;
						p = HttpCloud.addQuery(p, "key-marker", result.keyMarker);
						p = HttpCloud.addQuery(p, "version-id-marker", result.versionIdMarker);
						connect(new ListHandler(HttpMethod.GET, p));
					} else {
						setSuccess(list);
					}
				}
			};
		}
	}

	@Override
	public Future<List<UploadInfo>> startListUploads(final String remotePath) {
		// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadListMPUpload.html
		final String urlPath = HttpCloud.addQuery(encodePathPrefix(remotePath), "uploads");
		final List<UploadInfo> list = new ArrayList<>();
		return new AwsRequest<List<UploadInfo>>(this.cloud, this.configuration, this.host, HttpMethod.GET, urlPath) {
			@Override
			protected void success(InputStream content) throws Exception {
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
					String p = urlPath;
					p = HttpCloud.addQuery(p, "key-marker", result.nextKeyMarker);
					p = HttpCloud.addQuery(p, "upload-id-marker", result.nextUploadIdMarker);
					connect(new ListHandler(HttpMethod.GET, p));
				} else {
					setSuccess(list);
				}

			}
		};
	}

	@Override
	public Future<Void> startDelete(String remotePath, String version) {
		// http://docs.aws.amazon.com/AmazonS3/latest/API/RESTObjectDELETE.html
		String urlPathAndVersion = this.cloud.addVersion(HttpCloud.encodePath('/' + this.configuration.prefix + remotePath), version);

		return new AwsRequest<Void>(this.cloud, this.configuration, this.host, HttpMethod.DELETE, urlPathAndVersion) {
			@Override
			protected void success(InputStream content) throws Exception {
				setSuccess(null);
			}
		};
	}

	@Override
	public Future<Void> startDeleteUpload(String remotePath, String uploadId) {
		// http://docs.aws.amazon.com/AmazonS3/latest/API/mpUploadAbort.html
		String urlPath = HttpCloud.addQuery(HttpCloud.encodePath('/' + this.configuration.prefix + remotePath), "uploadId", uploadId);

		return new AwsRequest<Void>(this.cloud, this.configuration, this.host, HttpMethod.DELETE, urlPath) {
			@Override
			protected void success(InputStream content) throws Exception {
				setSuccess(null);
			}
		};
	}

	// helpers

	String encodePathPrefix(String remotePath) {
		if (!this.configuration.prefix.isEmpty())
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
		int pos = this.configuration.prefix.indexOf('/');
		if (pos != -1)
			return key.substring(this.configuration.prefix.length() - (pos + 1) + remotePath.length());

		pos = remotePath.indexOf('/');
		if (pos == -1) {
			// urlPath is just a bucket without trailing slash. ensure that urlPath + path is a new valid urlPath
			return '/' + key;
		}
		return key.substring(remotePath.length() - (pos + 1));
	}
}
