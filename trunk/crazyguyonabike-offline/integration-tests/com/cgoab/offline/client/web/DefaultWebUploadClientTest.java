package com.cgoab.offline.client.web;

import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

import org.apache.http.impl.client.BasicCookieStore;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.cgoab.offline.Application;
import com.cgoab.offline.client.CompletionCallback;
import com.cgoab.offline.client.web.DefaultWebUploadClient;

public class DefaultWebUploadClientTest {

	DefaultWebUploadClient impl = new DefaultWebUploadClient(Application.CRAZYGUYONABIKE_HOST,
			Application.CRAZYGUYONABIKE_PORT, new BasicCookieStore(), null);
	private static String password, username;

	static {
		System.out.println("Enter CGOAB username/password for test");
		System.out.print("Username: ");
		Scanner scanner = new Scanner(System.in);
		username = scanner.nextLine();
		System.out.print("Password: ");
		password = scanner.nextLine();
	}

	public static void main(String[] args) {
		System.out.println("'" + username + "' - '" + password + "'");
	}

	@Before
	public void clearCookieStore() {
		impl.getCookieStore().clear();
	}

	@After
	public void dispose() {
		impl.dispose();
	}

	private static class Callback<T> implements CompletionCallback<T> {
		T result;

		Throwable exception;

		CountDownLatch latch = new CountDownLatch(1);

		public Throwable getException() {
			return exception;
		}

		public T getResult() {
			return result;
		}

		public Throwable awaitError() throws InterruptedException {
			latch.await();
			if (exception == null) {
				throw new AssertionError("Expected exception");
			}
			return exception;
		}

		public void awaitNormalCompletion() throws InterruptedException {
			latch.await();
			if (exception != null) {
				AssertionError ex = new AssertionError("Got exception not result");
				ex.initCause(exception);
				throw ex;
			}

		}

		@Override
		public void retryNotify(Throwable exception, int retryCount) {
		}

		public void onCompletion(T result) {
			this.result = result;
			latch.countDown();
		};

		@Override
		public void onError(Throwable exception) {
			this.exception = exception;
			latch.countDown();
		}
	}
	
	@Test
	public void testLogin_sucess() throws InterruptedException {
		Callback<String> testCb = new Callback<String>();
		impl.login(username, password, testCb);
		testCb.awaitNormalCompletion();

		Assert.assertEquals(username, testCb.getResult());
		Assert.assertEquals(username, impl.getCurrentUsername());
		Assert.assertEquals("Ben Rowlands", impl.getCurrentUserRealName());
	}

	@Test
	public void testLogin_failure() throws InterruptedException {
		Callback<String> testCb = new Callback<String>();
		impl.login("blah", "blah", testCb);
		testCb.awaitError();

		Assert.assertEquals(null, testCb.getResult());
		Assert.assertEquals(null, impl.getCurrentUsername());
		Assert.assertEquals(null, impl.getCurrentUserRealName());
	}

	@Test
	public void testAutoLogin() throws InterruptedException {
		// first login correctly
		Callback<String> loginCb = new Callback<String>();
		impl.login(username, password, loginCb);
		loginCb.awaitNormalCompletion();
		Assert.assertEquals(username, loginCb.getResult());

		// // now logout, and try auto-login again
		// Callback<Void> logoutCb = new Callback<Void>();
		// impl.logout(logoutCb);
		// logoutCb.awaitNormalCompletion();

		// now login again with auto-login (from cookie)
		loginCb = new Callback<String>();
		impl.login(null, null, loginCb);
		loginCb.awaitNormalCompletion();
		Assert.assertEquals(username, loginCb.getResult());
	}

	@Test
	public void testAutoLogin_failsAfterLogout() throws InterruptedException {
		// first login correctly
		Callback<String> loginCb = new Callback<String>();
		impl.login(username, password, loginCb);
		loginCb.awaitNormalCompletion();
		Assert.assertEquals(username, loginCb.getResult());

		// now logout, and try auto-login again
		Callback<Void> logoutCb = new Callback<Void>();
		impl.logout(logoutCb);
		logoutCb.awaitNormalCompletion();

		// now login again with auto-login (from cookie)
		loginCb = new Callback<String>();
		impl.login(null, null, loginCb);
		loginCb.awaitError();
	}
}