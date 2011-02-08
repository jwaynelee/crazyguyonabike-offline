package com.cgoab.offline.client.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.util.Utils;

/**
 * Cookie store that writes cookies to disk when {@link #persist()} called.
 * 
 * @TODO persist in {@link #addCookie(Cookie)}?
 */
public class FileCookieStore implements CookieStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(FileCookieStore.class);

	private final CookieStore delegate = new BasicCookieStore();

	private final File cookieFile;

	public FileCookieStore(String file) {
		cookieFile = new File(file);
		if (cookieFile.exists()) {
			load();
		} else {
			try {
				Utils.createFile(cookieFile);
			} catch (IOException e) {
				LOGGER.warn("Failed to create cookie file store '" + cookieFile.getAbsolutePath() + "'", e);
			}
		}
	}

	@Override
	public void addCookie(Cookie cookie) {
		LOGGER.debug("Adding cookie: {}", cookie);
		delegate.addCookie(cookie);
	}

	@Override
	public void clear() {
		delegate.clear();
	}

	@Override
	public boolean clearExpired(Date date) {
		return delegate.clearExpired(date);
	}

	@Override
	public List<Cookie> getCookies() {
		return delegate.getCookies();
	}

	public void load() {
		if (!cookieFile.exists()) {
			return;
		}
		FileInputStream fis = null;
		Object o = null;
		try {
			fis = new FileInputStream(cookieFile);
			ObjectInputStream ois = new ObjectInputStream(fis);
			o = ois.readObject();
			ois.close();
		} catch (Exception e) {
			/* delete file */
			LOGGER.warn("Failed to read cookies file '" + cookieFile.getAbsolutePath()
					+ "'; deleting file in case it is corrupt", e);
			Utils.close(fis); // close stream & release fd before delete
			if (!cookieFile.delete()) {
				LOGGER.error("Failed to delete cookie file '" + cookieFile.getAbsolutePath() + "'");
			}
			return;
		} finally {
			Utils.close(fis);
		}

		if (!(o instanceof List)) {
			throw new IllegalStateException("Unexpected type");
		}

		for (Cookie c : (List<Cookie>) o) {
			addCookie(c);
		}
	}

	// writes current state of cookies to disk
	public void persist() {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(cookieFile);
			Serializable list = (Serializable) delegate.getCookies();
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(list);
			oos.close();
		} catch (IOException e) {
			LOGGER.warn("Failed to write to cookies file [" + cookieFile.getAbsolutePath() + "]", e);
		} finally {
			Utils.close(fos);
		}
	}

	@Override
	public String toString() {
		return String.format("%s file=%s: %s", getClass().getSimpleName(), cookieFile.getAbsolutePath(), delegate);
	}
}