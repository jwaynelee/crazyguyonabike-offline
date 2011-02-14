package com.cgoab.offline.client.web;

import java.util.concurrent.Executor;

import org.apache.http.client.CookieStore;

import com.cgoab.offline.client.UploadClient;
import com.cgoab.offline.client.UploadClientFactory;
import com.cgoab.offline.util.Assert;

public class DefaultWebUploadClientFactory extends UploadClientFactory {

	private CookieStore cookies;
	private Executor executor;
	private int maxBackoffDelay = Integer.MAX_VALUE;

	@Override
	public UploadClient newClient() {
		return new DefaultWebUploadClient(getHost(), getPort(), cookies, executor, maxBackoffDelay);
	}

	public void setCallbackExecutor(Executor executor) {
		this.executor = executor;
	}

	public void setCookies(CookieStore cookies) {
		this.cookies = cookies;
	}

	public void setMaximumBackoffDelay(int maxSleepInMs) {
		Assert.isTrue(maxSleepInMs > 0);
		this.maxBackoffDelay = maxSleepInMs;
	}
}
