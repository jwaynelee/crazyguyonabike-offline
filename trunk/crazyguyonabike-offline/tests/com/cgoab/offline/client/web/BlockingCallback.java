package com.cgoab.offline.client.web;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import testutils.TestProperties;

import com.cgoab.offline.client.CompletionCallback;

public class BlockingCallback<T> implements CompletionCallback<T> {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultWebUploadClientTest.class);

	private CountDownLatch latch = new CountDownLatch(1);

	T result;

	Throwable exception;

	public T get() throws Throwable {
		if (TestProperties.waitForever) {
			latch.await();
		} else {
			int loops = 0;
			int timePerLoop = 5;
			while (!latch.await(timePerLoop, TimeUnit.SECONDS)) {
				loops++;
				LOG.info("Waited {}s for completion", loops * timePerLoop);
				if (loops > 10) {
					throw new TimeoutException();
				}
			}
		}
		/* reset */
		if (exception != null) {
			throw exception;
		}
		return result;
	}

	@Override
	public void onCompletion(T result) {
		this.result = result;
		latch.countDown();
	}

	@Override
	public void onError(Throwable exception) {
		this.exception = exception;
		latch.countDown();
	}

	@Override
	public void retryNotify(Throwable exception, int retryCount) {
		/* ignore */
	}
}