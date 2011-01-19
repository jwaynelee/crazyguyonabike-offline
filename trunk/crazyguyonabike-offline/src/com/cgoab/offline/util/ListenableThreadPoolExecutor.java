package com.cgoab.offline.util;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ListenableThreadPoolExecutor extends ThreadPoolExecutor {

	/**
	 * Returns a new ListenableThreadPoolExecutor with the same number of
	 * threads as processors less 1, or 1.
	 * 
	 * @param name
	 * @param priority
	 * @return
	 */
	public static ListenableThreadPoolExecutor newOptimalSizedExecutorService(String name, int priority) {
		int processors = Runtime.getRuntime().availableProcessors();
		int threads = processors > 1 ? processors - 1 : 1;
		return new ListenableThreadPoolExecutor(name, threads, priority);
	}

	public ListenableThreadPoolExecutor(final String name, int threads, final int priority) {
		super(threads, threads, 0l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		setThreadFactory(new ThreadFactory() {
			AtomicInteger threadCounter = new AtomicInteger();

			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r);
				thread.setDaemon(true);
				thread.setName(name + "#" + threadCounter.getAndIncrement());
				thread.setPriority(priority);
				return thread;
			}
		});
	}

	@Override
	protected <T extends Object> RunnableFuture<T> newTaskFor(final Callable<T> callable) {
		return new FutureTask<T>(callable) {
			@Override
			protected void done() {
				if (callable instanceof ListenableCancellableTask) {
					ListenableCancellableTask<T> task = (ListenableCancellableTask<T>) callable;
					if (isCancelled()) {
						task.cancel();
					}
					task.onCompletion(this);
				}
			}
		};
	}
}
