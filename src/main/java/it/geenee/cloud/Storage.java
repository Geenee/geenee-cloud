package it.geenee.cloud;

import io.netty.util.concurrent.Future;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;
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
	 * Download a file
	 * @param file file to download to
	 * @param remotePath path to file in cloud storage
	 * @param version version to download, null for current version
	 * @return
	 */
	Transfer download(FileChannel file, String remotePath, String version) throws IOException;

	/**
	 * Upload a file
	 * @param file file to upload from
	 * @param remotePath path to file in cloud storage
	 * @return
	 */
	Transfer upload(FileChannel file, String remotePath) throws IOException;


	Future<List<Instance>> requestList(String remotePath);

	Future<List<Upload>> requestIncompleteUploads(String remotePath);
	default List<Upload> getIncompleteUploads(String remotePath) throws InterruptedException, ExecutionException {
		return requestIncompleteUploads(remotePath).get();
	}
}
