package it.geenee.cloud;


public interface Transfer {

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
	 * Cancels the transfer. If not complete yet, getState() will return FAILED
	 */
	void cancel();

	/**
	 * Wait until download is finished
	 */
	void waitForStateChange() throws InterruptedException;




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
			DONE,

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
	 * Get number of parts
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
	 * Get the id of the transfer that is needed to continue it. Returns null if none is needed.
	 * @return transfer id
	 */
	String getId();
}