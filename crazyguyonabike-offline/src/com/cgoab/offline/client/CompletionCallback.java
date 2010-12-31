package com.cgoab.offline.client;

/**
 * Listener for the completion of operations invoked on a {@link UploadClient} .
 **/
public interface CompletionCallback<T> {

	/**
	 * Called when the operation completed successfully, typically this is
	 * called from an executor thread.
	 * 
	 * @param result
	 */
	public void onCompletion(T result);

	/**
	 * Called when the operation failed, typically this is called from an
	 * executor thread.
	 * 
	 * @param exception
	 */
	public void onError(Throwable exception);

	/**
	 * Notification that an operation will be retried due to a failure.
	 * 
	 * @param exception
	 * @param retryCount
	 */
	public void retryNotify(Throwable exception, int retryCount);
}
