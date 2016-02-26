package it.geenee.cloud;

import io.netty.util.concurrent.Future;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;


public interface Storage {

	/**
	 * Calculate hash of a file
	 * @param file file to calculate the checksum of
	 * @return hash of file calculated using the same algorithm that the cloud storage uses. May depend on configuration.partSize
	 * @throws Exception
	 */
	String calculateHash(FileChannel file) throws Exception;

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
	default void download(FileChannel file, String remotePath, String version) throws InterruptedException, ExecutionException {
		startDownload(file, remotePath, version).get();
	}

	/**
	 * Upload a file
	 * @param file file to upload from
	 * @param remotePath path to file in cloud storage
	 * @return
	 */
	Transfer startUpload(FileChannel file, String remotePath);
	default void upload(FileChannel file, String remotePath) throws InterruptedException, ExecutionException {
		startUpload(file, remotePath).get();
	}

	/**
	 * Get a list of all files that have the given remote path as prefix. The result is returned as a map from file path to file hash which is convenient
	 * for synchronizing a local directory with a remote directory
	 * @param remotePath
	 * @return map of file path to file hash
	 */
	Future<Map<String, String>> startGetFiles(String remotePath);
	default Map<String, String> getFiles(String remotePath) throws InterruptedException, ExecutionException {
		return startGetFiles(remotePath).get();
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
	 * @param mode list mode
	 * @return list of files
	 */
	Future<List<FileInfo>> startGetFiles(String remotePath, ListMode mode);
	default List<FileInfo> getFiles(String remotePath, ListMode mode) throws InterruptedException, ExecutionException {
		return startGetFiles(remotePath, mode).get();
	}

	/**
	 * Get a list of all incomplete uploads for files that have the given remote path as prefix
	 * @param remotePath
	 * @return list of incomplete uploads
	 */
	Future<List<UploadInfo>> startGetUploads(String remotePath);
	default List<UploadInfo> getUploads(String remotePath) throws InterruptedException, ExecutionException {
		return startGetUploads(remotePath).get();
	}

	/**
	 * Delete a file on the cloud storage
	 * @param remotePath
	 * @param version
	 * @return
	 */
	Future<Void> startDeleteFile(String remotePath, String version);
	default void deleteFile(String remotePath, String version) throws InterruptedException, ExecutionException {
		startDeleteFile(remotePath, version).get();
	}

	Future<Void> startDeleteUpload(String remotePath, String uploadId);
	default void deleteUpload(String remotePath, String uploadId) throws InterruptedException, ExecutionException {
		startDeleteUpload(remotePath, uploadId).get();
	}
}
