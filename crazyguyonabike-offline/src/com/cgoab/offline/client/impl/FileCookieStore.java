package com.cgoab.offline.client.impl;

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

/**
 * Cookie store that writes cookies to disk when {@link #persist()} called.
 * 
 * @TODO persist in {@link #addCookie(Cookie)}?
 */
public class FileCookieStore implements CookieStore {

	CookieStore delegate = new BasicCookieStore();

	private File FILE = new File(System.getProperty("user.home") + File.separator + ".cgoaboffline" + File.separator
			+ "cookies");

	public FileCookieStore() {
		load();
	}

	// writes current state of cookies to disk
	public void persist() {
		FileOutputStream os = null;
		try {
			os = new FileOutputStream(FILE);
			Serializable list = (Serializable) delegate.getCookies();
			ObjectOutputStream oos = new ObjectOutputStream(os);
			oos.writeObject(list);
			oos.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public void load() {
		if (!FILE.exists()) {
			return;
		}
		FileInputStream is = null;
		Object o;
		try {
			is = new FileInputStream(FILE);
			ObjectInputStream ois = new ObjectInputStream(is);
			o = ois.readObject();
			ois.close();
			// } catch (SerializationException e) {
			// TODO log, delete file and ignore
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}

		if (!(o instanceof List)) {
			throw new IllegalStateException("Unexpected type");
		}

		for (Cookie c : (List<Cookie>) o) {
			addCookie(c);
		}
	}

	public void addCookie(Cookie cookie) {
		System.out.println("--- Saving Cookie: " + cookie);
		delegate.addCookie(cookie);
	}

	public List<Cookie> getCookies() {
		return delegate.getCookies();
	}

	public boolean clearExpired(Date date) {
		return delegate.clearExpired(date);
	}

	public void clear() {
		delegate.clear();
	}
}