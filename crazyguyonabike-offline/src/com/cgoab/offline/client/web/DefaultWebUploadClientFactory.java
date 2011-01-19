package com.cgoab.offline.client.web;

import java.util.concurrent.Executor;

import org.apache.http.client.CookieStore;

import com.cgoab.offline.client.UploadClient;
import com.cgoab.offline.client.UploadClientFactory;

public class DefaultWebUploadClientFactory extends UploadClientFactory {

	private CookieStore cookies;
	private Executor executor;

	@Override
	public UploadClient newClient() {
		return new DefaultWebUploadClient(getHost(), getPort(), cookies, executor);
	}

	public void setCallbackExecutor(Executor executor) {
		this.executor = executor;
	}

	public void setCookies(CookieStore cookies) {
		this.cookies = cookies;
	}
}
