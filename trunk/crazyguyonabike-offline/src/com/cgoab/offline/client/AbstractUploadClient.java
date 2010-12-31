package com.cgoab.offline.client;

import java.io.IOException;


public abstract class AbstractUploadClient implements UploadClient {

	private Task<?> currentTask;
	private final Object lock = new Object();
	private Thread worker;

	protected final Task<Object> DEATH = new Task<Object>(null) {
		@Override
		protected Object doRun() throws Exception {
			throw new AssertionError("SHOULD NOT RUN");
		}
	};
	private String threadName = "ClientThread";

	protected void setThreadName(String threadName) {
		this.threadName = threadName;
	}

	protected Thread getWorkerThread() {
		return worker;
	}

	private Thread createAndStartWorker() {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (true) {
						Task<?> toRun;
						synchronized (lock) {
							while ((toRun = currentTask) == null) {
								lock.wait();
							}
						}
						if (toRun == DEATH) {
							return;
						} else {
							toRun.invoke();
						}
					}
				} catch (InterruptedException e) {
					/* quit */
				}
			}
		});
		t.setName(threadName);
		t.start();
		return t;
	}

	private void throwIfBusyOrDisposed() {
		synchronized (lock) {
			if (currentTask == DEATH) {
				throw new IllegalStateException("Already disposed");
			}
			if (currentTask != null) {
				throw new IllegalStateException("Operation in progress, wait until completion before invoking another");
			}
		}
	}

	/**
	 * Called by the work thread when a retry occurs
	 * 
	 * @param exception
	 * @param executionCount
	 */
	protected void fireOnRetry(IOException exception, int executionCount) {
		Task<?> current;
		synchronized (lock) {
			current = currentTask;
		}

		if (current != null) {
			current.callback.retryNotify(exception, executionCount);
		}
	}

	protected void asyncExec(Task<?> task) {
		synchronized (lock) {
			throwIfBusyOrDisposed();
			if (worker == null) {
				worker = createAndStartWorker();
			}
			assert currentTask == null;
			currentTask = task;
			lock.notifyAll();
		}
	}

	public abstract class Task<T> {

		CompletionCallback<T> callback;

		protected Task(CompletionCallback<T> callback) {
			this.callback = callback;
		}

		final void invoke() {
			Throwable ex = null;
			T result = null;
			try {
				result = doRun();
			} catch (Throwable t) {
				t.printStackTrace();
				ex = t;
			}

			/* Clean-up and callback */

			synchronized (lock) {
				// clear to allow callback to add more work (ie, recurse)
				currentTask = null;
			}

			// invoke listener outside of try/catch
			if (ex == null) {
				callback.onCompletion(result);
			} else {
				callback.onError(ex);
			}
		}

		protected abstract T doRun() throws Exception;

		protected void cancel() {
		}
	}

	public void dispose() {
		synchronized (lock) {
			currentTask = DEATH;
			worker.interrupt();
		}
	}

	public void cancel() {
		Task<?> current;
		synchronized (lock) {
			current = currentTask;
		}
		if (current != null) {
			current.cancel();
		}
	}
}