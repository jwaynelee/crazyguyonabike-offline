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

	private final File file;

	public FileCookieStore(String cookieFile) {
		file = new File(cookieFile);
		if (!file.exists()) {
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			try {
				file.createNewFile();
			} catch (IOException e) {
				LOGGER.warn("Failed to create cookie file store '" + file.getAbsolutePath() + "'", e);
			}
		}
		load();
	}

	@Override
	public void addCookie(Cookie cookie) {
		LOGGER.info("Saving Cookie [{}]", cookie);
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
		if (!file.exists()) {
			return;
		}
		FileInputStream fis = null;
		Object o = null;
		try {
			fis = new FileInputStream(file);
			ObjectInputStream ois = new ObjectInputStream(fis);
			o = ois.readObject();
			ois.close();
		} catch (Exception e) {
			/* delete file */
			LOGGER.warn("Failed to read cookies file [" + file.getAbsolutePath()
					+ "], deleting cookie file in case it is corrupt", e);
			Utils.close(fis); // close stream first to release file
			file.delete();
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
			fos = new FileOutputStream(file);
			Serializable list = (Serializable) delegate.getCookies();
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(list);
			oos.close();
		} catch (IOException e) {
			LOGGER.warn("Failed to write to cookies file [" + file.getAbsolutePath() + "]", e);
		} finally {
			Utils.close(fos);
		}
	}

	@Override
	public String toString() {
		return String.format("%s file=%s: %s", getClass().getSimpleName(), file.getAbsolutePath(), delegate);
	}
}