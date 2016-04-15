package it.geenee.cloud;

import io.netty.util.concurrent.Future;

import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;


public interface Storage {

	Cloud.Configuration getConfiguration();

	/**
	 * Calculate hash of a file
	 * @param file file to calculate the checksum of
	 * @return hash of file calculated using the same algorithm that the cloud storage uses. May depend on configuration.partSize
	 * @throws Exception
	 */
	String hash(FileChannel file) throws Exception;

	/**
	 * returns the url of the storage extended by the given remote path, e.g. "https://s3.eu-central-1.amazonaws.com/foo/bar"
	 * @param remotePath
	 * @return url of storage
	 */
	String getUrl(String remotePath);

	/**
	 * Download a file
	 * @param file file to download to
	 * @param remotePath path to file in cloud storage
	 * @param version version to download, null for current version
	 * @return
	 */
	Transfer startDownload(FileChannel file, String remotePath, String version);
	default FileInfo download(FileChannel file, String remotePath, String version) throws InterruptedException, ExecutionException {
		return startDownload(file, remotePath, version).get();
	}

	/**
	 * Upload a file
	 * @param file file to upload from
	 * @param remotePath path to file in cloud storage
	 * @return
	 */
	Transfer startUpload(FileChannel file, String remotePath);
	default FileInfo upload(FileChannel file, String remotePath) throws InterruptedException, ExecutionException {
		return startUpload(file, remotePath).get();
	}

	/**
	 * Get file info for given path
	 * @param remotePath
	 * @param version optional version to get info for
	 * @return file info. The latest flag is always false if a version is given even if the latest version was requested
	 */
	Future<FileInfo> startGetInfo(String remotePath, String version);
	default FileInfo getInfo(String remotePath, String version) throws InterruptedException, ExecutionException {
		return startGetInfo(remotePath, version).get();
	}

	/**
	 * Request a list of all files that have the given remote path as prefix
	 * @param remotePath
	 * @return list of file paths
	 */
	Future<String[]> startList(String remotePath);
	default String[] list(String remotePath) throws InterruptedException, ExecutionException {
		return this.startList(remotePath).get();
	}

	enum ListMode {
		// only latest versions are listed with no version information
		UNVERSIONED,

		// only latest versions are listed
		VERSIONED_LATEST,

		// all versions are listed
		VERSIONED_ALL,

		// only latest versions and delete markers are listed
		VERSIONED_DELETEED_LATEST,

		// all versions and delete markers are listed
		VERSIONED_DELETED_ALL
	}

	/**
	 * Request a list of all files that have the given remote path as prefix
	 * @param remotePath
	 * @param mode list mode, one member of ListMode enum
	 * @return list of file infos
	 */
	Future<FileInfo[]> startList(String remotePath, ListMode mode);
	default FileInfo[] list(String remotePath, ListMode mode) throws InterruptedException, ExecutionException {
		return this.startList(remotePath, mode).get();
	}

	/**
	 * Get a list of all files that have the given remote path as prefix. The result is returned as a map from file path to file hash which is convenient
	 * for synchronizing a local directory with a remote directory
	 * @param remotePath
	 * @return map of file path to file hash
	 */
	Future<Map<String, String>> startListHashes(String remotePath);
	default Map<String, String> listHashes(String remotePath) throws InterruptedException, ExecutionException {
		return startListHashes(remotePath).get();
	}

	/**
	 * Get a list of all incomplete uploads for files that have the given remote path as prefix
	 * @param remotePath
	 * @return list of incomplete uploads
	 */
	Future<List<UploadInfo>> startListUploads(String remotePath);
	default List<UploadInfo> listUploads(String remotePath) throws InterruptedException, ExecutionException {
		return startListUploads(remotePath).get();
	}

	/**
	 * Delete a file on the cloud storage
	 * @param remotePath
	 * @param version optional version to delete
	 * @return
	 */
	Future<Void> startDelete(String remotePath, String version);
	default void delete(String remotePath, String version) throws InterruptedException, ExecutionException {
		startDelete(remotePath, version).get();
	}

	Future<Void> startDeleteUpload(String remotePath, String uploadId);
	default void deleteUpload(String remotePath, String uploadId) throws InterruptedException, ExecutionException {
		startDeleteUpload(remotePath, uploadId).get();
	}
}
