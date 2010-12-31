package com.cgoab.offline.util;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * A ask that when submitted to a {@link ListenableThreadPoolExecutor} provides
 * notification to clients via the {@link FutureCompletionListener} interface.
 */
public abstract class ListenableCancellableTask<T> implements Callable<T> {

	private FutureCompletionListener<T> listener;
	private Object data;

	public ListenableCancellableTask(FutureCompletionListener<T> listener, Object data) {
		this.listener = listener;
		this.data = data;
	}

	/**
	 * Called by the {@link ListenableThreadPoolExecutor} when a task completes
	 * (done or cancelled).
	 * 
	 * @param future
	 */
	final void onCompletion(Future<T> future) {
		if (listener != null) {
			listener.onCompletion(future, data);
		}
	}

	/**
	 * Tasks may override this to implement their cancellation when requested,
	 * by default a TPE will only attempt to interrupt a thread to cancel it.
	 */
	protected void cancel() {
	}
}