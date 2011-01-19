package com.cgoab.offline.client;

public abstract class UploadClientFactory {

	private String host;
	private int port;

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public abstract UploadClient newClient();

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}
}
