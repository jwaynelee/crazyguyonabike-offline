package com.cgoab.offline.client;

import java.io.IOException;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractUploadClient implements UploadClient {
	protected final Logger LOG = LoggerFactory.getLogger(getClass());
	private Task<?> currentTask;
	private final Object lock = new Object();
	private Thread worker;

	private final Task<Object> DEATH = new Task<Object>(null) {
		@Override
		protected Object doRun() throws Exception {
			throw new AssertionError("SHOULD NEVER RUN");
		}
	};
	private String threadName = "UploadClient";
	private Executor callbackExecutor;

	public AbstractUploadClient(Executor executor) {
		callbackExecutor = executor;
	}

	public Executor getCallbackExecutor() {
		return callbackExecutor;
	}

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
							/* quit */
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

		if (current != null && current.callback != null) {
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
				long start = System.currentTimeMillis();
				LOG.debug("Starting [{}]", this);
				result = doRun();
				LOG.debug("[{}] completed in {}ms", this, System.currentTimeMillis() - start);
			} catch (Throwable t) {
				LOG.info("[" + this + "] failed", t);
				ex = t;
			}

			synchronized (lock) {
				/* clear task so callback can add more work */
				currentTask = null;
			}

			/* listener invoked outside try/catch AND outside lock */
			CallbackRunner<T> cmd = new CallbackRunner<T>(callback, result, ex);
			if (callbackExecutor != null) {
				callbackExecutor.execute(cmd);
			} else {
				cmd.run();
			}
		}

		protected abstract T doRun() throws Exception;

		protected void cancel() {
		}

		@Override
		public String toString() {
			return getClass().getSimpleName();
		}
	}

	static class CallbackRunner<T> implements Runnable {
		private final CompletionCallback<T> callback;
		private final Throwable ex;
		private final T result;

		public CallbackRunner(CompletionCallback<T> callback, T result, Throwable ex) {
			this.callback = callback;
			this.ex = ex;
			this.result = result;
		}

		@Override
		public void run() {
			if (ex == null) {
				callback.onCompletion(result);
			} else {
				callback.onError(ex);
			}
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