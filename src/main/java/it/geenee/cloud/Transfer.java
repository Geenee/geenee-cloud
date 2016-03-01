package it.geenee.cloud;


import io.netty.util.concurrent.Future;

public interface Transfer extends Future<Void> {

	enum State {
		/**
		 * initiating transfer, e.g. get download file length and version or initiate upload
		 */
		INITIATING,

		/**
		 * transfer is in progress
		 */
		PROGRESS,

		/**
		 * completing transfer
		 */
		COMPLETING,

		/**
		 * transfer completed successfully
		 */
		SUCCESS,

		/**
		 * transfer was cancelled
		 */
		CANCELLED,

		/**
		 * transfer has failed
		 */
		FAILED
	}

	/**
	 * Get state
	 * @return state
	 */
	State getState();

	/**
	 * Wait until the state of the transfer or of one part changes
	 */
	void waitForStateChange() throws InterruptedException;

	/**
	 * Cancels the transfer. If not done yet, getState() will return CANCELLED
	 */
	default void cancel() {
		cancel(false);
	}




	/**
	 * Part of multipart uploads and downloads. Single part uploads and downloads have one part spanning the entire file.
	 */
	interface Part {
		enum State {
			/**
			 * part is queued for transfer
			 */
			QUEUED,

			/**
			 * part is waiting for retry
			 */
			RETRY,

			/**
			 * initiating transfer of part
			 */
			INITIATING,

			/**
			 * part transfer is in progress
			 */
			PROGRESS,

			/**
			 * part is sucessfully transferred
			 */
			SUCCESS,

			/**
			 * part has failed
			 */
			FAILED
		}

		/**
		 * Returns 0-based index of part
		 */
		int getIndex();

		/**
		 * Returns offset of part in file
		 */
		long getOffset();

		/**
		 * Returns length of part
		 */
		int getLength();

		/**
		 * Returns state of part
		 */
		State getState();

		/**
		 * Returns retry count
		 */
		int getRetryCount();

		/**
		 * Get the id of the transfer that is needed to continue it. Returns null if none is needed.
		 * @return transfer id
		 */
		String getId();
	}

	/**
	 * Get url of file in cloud storage
	 * @return url of file
	 */
	String getUrl();

	/**
	 * Get number of parts if not in INITIATING state
	 * @return number of parts
	 */
	int getPartCount();

	/**
	 * Get a part
	 * @return part at index
	 */
	Part getPart(int index);

	/**
	 * Get the hash of the file in the cloud storage. For downloads it is available if getState() is at least PROGRESS, for uploads
	 * it is available if getState() is SUCCESS.
	 * @return hash of file
	 */
	String getHash();

	/**
	 * Get the version of the file in the cloud storage. For downloads it is available if getState() is at least PROGRESS, for uploads
	 * it is available if getState() is SUCCESS.
	 * @return version of file or null if versioning is not supported
	 */
	String getVersion();

	/**
	 * Get the id of the transfer or null if the transfer has no id. For example a multiplart upload has an id to continue or cancel it.
	 * @return transfer id
	 */
	String getId();
}