package com.cgoab.offline.client.web;

import org.apache.http.client.CookieStore;

import com.cgoab.offline.client.UploadClient;
import com.cgoab.offline.client.UploadClientFactory;

public class DefaultWebUploadClientFactory extends UploadClientFactory {

	private CookieStore cookies;

	public void setCookies(CookieStore cookies) {
		this.cookies = cookies;
	}

	@Override
	public UploadClient newClient() {
		return new DefaultWebUploadClient(getHost(), getPort(), cookies);
	}
}
