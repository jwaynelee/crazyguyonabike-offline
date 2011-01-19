package com.cgoab.offline.client.web;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.apache.http.impl.client.BasicCookieStore;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import testutils.TestLogSetup;
import testutils.TestUtils;
import testutils.photos.TestPhotos;

import com.cgoab.offline.Application;
import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.web.DefaultWebUploadClient.ServerOperationException;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;

public class DefaultWebUploadClientUsingCGOABServerTest {

	DefaultWebUploadClient client = new DefaultWebUploadClient(Application.CRAZYGUYONABIKE_HOST,
			Application.CRAZYGUYONABIKE_PORT, new BasicCookieStore(), new CallingThreadExecutor());

	private static String password = "secret", username = "testcgoaboffline";

	static {
		TestLogSetup.configure();
		// System.out.println("Enter CGOAB username/password for test");
		// System.out.print("Username: ");
		// Scanner scanner = new Scanner(System.in);
		// username = scanner.nextLine();
		// System.out.print("Password: ");
		// password = scanner.nextLine();
	}

	public static void main(String[] args) {
		System.out.println("'" + username + "' - '" + password + "'");
	}

	@Before
	public void clearCookieStore() {
		client.getCookieStore().clear();
	}

	@After
	public void dispose() {
		client.dispose();
	}

	@Test
	public void login_sucess() throws Throwable {
		BlockingCallback<String> testCb = new BlockingCallback<String>();
		client.login(username, password, testCb);
		Assert.assertEquals(username, testCb.get());
		Assert.assertEquals(username, client.getCurrentUsername());
		Assert.assertEquals("test cgoab-offline", client.getCurrentUserRealName());
	}

	@Test
	public void login_failure() throws Throwable {
		BlockingCallback<String> testCb = new BlockingCallback<String>();
		client.login("blah", "blah", testCb);
		try {
			testCb.get();
			fail("exception expected");
		} catch (ServerOperationException e) {
			/* ok */
		}

		Assert.assertEquals(null, client.getCurrentUsername());
		Assert.assertEquals(null, client.getCurrentUserRealName());
	}

	@Test
	public void autoLogin() throws Throwable {
		// first login correctly
		BlockingCallback<String> loginCb = new BlockingCallback<String>();
		client.login(username, password, loginCb);
		Assert.assertEquals(username, loginCb.get());

		// // now logout, and try auto-login again
		// BlockingCallback<Void> logoutCb = new BlockingCallback<Void>();
		// impl.logout(logoutCb);
		// logoutCb.get();

		// now login again with auto-login (from cookie)
		loginCb = new BlockingCallback<String>();
		client.login(null, null, loginCb);
		Assert.assertEquals(username, loginCb.get());
	}

	@Test
	public void autoLogin_failsAfterLogout() throws Throwable {
		// first login correctly
		BlockingCallback<String> loginCb = new BlockingCallback<String>();
		client.login(username, password, loginCb);
		Assert.assertEquals(username, loginCb.get());

		// now logout, and try auto-login again
		BlockingCallback<Void> logoutCb = new BlockingCallback<Void>();
		client.logout(logoutCb);
		logoutCb.get();

		// now login again with auto-login (from cookie)
		loginCb = new BlockingCallback<String>();
		client.login(null, null, loginCb);
		try {
			loginCb.get();
			fail("exception excpected");
		} catch (ServerOperationException e) {
			/* ok */
		}
	}

	@Test
	public void exampleUploadScenario() throws Throwable {
		/* login */
		BlockingCallback<String> loginBlock = new BlockingCallback<String>();
		client.login(username, password, loginBlock);
		assertEquals(username, loginBlock.get());

		/* documents */
		BlockingCallback<List<DocumentDescription>> docBlock = new BlockingCallback<List<DocumentDescription>>();
		client.getDocuments(docBlock);
		List<DocumentDescription> docs = docBlock.get();
		assertEquals(1, docs.size());
		assertEquals("TestJournal", docs.get(0).getTitle());
		int testDocId = 8149;
		assertEquals(testDocId, docs.get(0).getDocumentId());

		/* init */
		BlockingCallback<Void> initBlock = new BlockingCallback<Void>();
		client.initialize(testDocId, initBlock);
		initBlock.get();

		/* create page */
		Journal journal = new Journal(null, "test");
		Page page = journal.createNewPage();
		page.setTitle("TEST-" + DateTimeFormat.mediumDateTime().print(new LocalDateTime()));
		page.setHeadline(TestUtils.getTestName());
		page.setText("Hello world");
		Photo photo0 = new Photo(TestPhotos.extractSmallPhoto());
		photo0.setCaption("caption #0");
		/* extracted with new file name */
		Photo photo1 = new Photo(TestPhotos.extractSmallPhoto());
		photo1.setCaption("caption #1");
		page.addPhotos(Arrays.asList(photo0, photo1), 0);
		BlockingCallback<Integer> pageBlock = new BlockingCallback<Integer>();
		client.createNewPage(page, pageBlock);
		Integer pageId = pageBlock.get();
		Assert.assertThat(pageId, greaterThan(0));

		/* add photos */
		BlockingCallback<Void> photo0Block = new BlockingCallback<Void>();
		client.addPhoto(pageId, photo0, photo0Block, null);
		photo0Block.get();
		BlockingCallback<Void> photo1Block = new BlockingCallback<Void>();
		client.addPhoto(pageId, photo1, photo1Block, null);
		photo1Block.get();

		/* TODO check page is on server (list?), delete page */

		/* delete the page we just created */
		BlockingCallback<Void> deleteBlock = new BlockingCallback<Void>();
		client.deletePage(pageId, deleteBlock);
		deleteBlock.get();
	}
}