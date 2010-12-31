package com.cgoab.offline.util;

import java.util.concurrent.Future;

/**
 * Called when a future transitions to the done state, either cancelled or
 * completed.
 * 
 * @see ListenableThreadPoolExecutor
 */
public interface FutureCompletionListener<T> {
	/**
	 * Called when the future is complete, that is, calling {@link Future#get()}
	 * will never block but will return a result or raise an exception.
	 * 
	 * @param result
	 * @param data
	 */
	void onCompletion(Future<T> result, Object data);
}