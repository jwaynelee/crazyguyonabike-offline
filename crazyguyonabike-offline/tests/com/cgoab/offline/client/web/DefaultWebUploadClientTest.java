package com.cgoab.offline.client.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.apache.http.impl.client.BasicCookieStore;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import testutils.TestLogSetup;
import testutils.fakeserver.FakeCGOABServer;
import testutils.fakeserver.FakeCGOABModel.ServerJournal;
import testutils.fakeserver.FakeCGOABModel.ServerPage;
import testutils.photos.TestPhotos;

import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.web.DefaultWebUploadClient.ServerOperationException;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Page.HeadingStyle;
import com.cgoab.offline.model.Photo;

/**
 * Integration test using a "fake" HTTP server.
 */
public class DefaultWebUploadClientTest {

	DefaultWebUploadClient client;
	static FakeCGOABServer server;

	static {
		TestLogSetup.configure();
	}

	@BeforeClass
	public static void create() throws Exception {
		server = new FakeCGOABServer(0);
		server.getModel().createDefaultModel();
	}

	@AfterClass
	public static void destroy() {
		server.shutdown();
	}

	@Before
	public void beforeTest() {
		client = new DefaultWebUploadClient("localhost", server.getHttpServer().getLocalPort(), new BasicCookieStore(),
				new CallingThreadExecutor());
	}

	@After
	public void afterTest() {
		client.dispose();
	}

	@Test
	public void loginSucess() throws Throwable {
		BlockingCallback<String> completion = new BlockingCallback<String>();
		client.login("bob", "secret", completion);
		String result = completion.get();
		assertEquals("bob", result);
		assertEquals("bob", client.getCurrentUsername());
		assertEquals("Billy Bob", client.getCurrentUserRealName());
	}

	@Test
	public void loginFails() throws Throwable {
		BlockingCallback<String> completion = new BlockingCallback<String>();
		client.login("bob", "incorrect_password", completion);
		ServerOperationException exception = null;
		try {
			completion.get();/* should fail */
		} catch (ServerOperationException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertNotNull(exception.getHtml());
		assertEquals(null, client.getCurrentUsername());
		assertEquals(null, client.getCurrentUserRealName());
	}

	@Test
	public void autoLogin() throws Throwable {
		/* should fail */
		BlockingCallback<String> login1 = new BlockingCallback<String>();
		client.login(null, null, login1);
		try {
			login1.get();
			fail("login expected to fail");
		} catch (ServerOperationException e) {
			/* ok */
		}

		/* login normally */
		BlockingCallback<String> login2 = new BlockingCallback<String>();
		client.login("bob", "secret", login2);
		login2.get();

		/* login should work using cookie */
		BlockingCallback<String> login3 = new BlockingCallback<String>();
		client.login(null, null, login3);
		assertEquals("bob", login3.get());
	}

	@Test
	public void getDocuments() throws Throwable {
		/* login */
		BlockingCallback<String> loginCompletion = new BlockingCallback<String>();
		client.login("bob", "secret", loginCompletion);
		assertEquals("bob", loginCompletion.get());

		/* get documents */
		BlockingCallback<List<DocumentDescription>> completion = new BlockingCallback<List<DocumentDescription>>();
		client.getDocuments(completion);
		List<DocumentDescription> documents = completion.get();
		assertEquals(3, documents.size());
		assertEquals("Doc1", documents.get(0).getTitle());
		assertEquals(1, documents.get(0).getDocumentId());
		assertEquals("Doc2", documents.get(1).getTitle());
		assertEquals(2, documents.get(1).getDocumentId());
		assertEquals("Doc3", documents.get(2).getTitle());
		assertEquals(3, documents.get(2).getDocumentId());
	}

	@Test
	public void addPageAddPhotos() throws Throwable {
		/* login */
		BlockingCallback<String> loginCompletion = new BlockingCallback<String>();
		client.login("bob", "secret", loginCompletion);
		assertEquals("bob", loginCompletion.get());

		/* get documents */
		BlockingCallback<List<DocumentDescription>> completion = new BlockingCallback<List<DocumentDescription>>();
		client.getDocuments(completion);
		List<DocumentDescription> documents = completion.get();

		int docid = documents.get(0).getDocumentId();

		/* init the client */
		BlockingCallback<Void> initCallback = new BlockingCallback<Void>();
		client.initialize(docid, initCallback);
		initCallback.get();

		/* add page & photo */
		BlockingCallback<Integer> createPageCallback = new BlockingCallback<Integer>();
		Journal clientJournal = new Journal(null, "<bogus>");
		Page clientPage = clientJournal.createNewPage();
		clientPage.setTitle("title");
		clientPage.setHeadline("headline");
		clientPage.setBold(true);
		clientPage.setItalic(true);
		clientPage.setIndent(5);
		clientPage.setHeadingStyle(HeadingStyle.MEDIUM);
		clientPage.setDistance(10);
		clientPage.setDate(new LocalDate(2011, 1, 1));
		clientPage.setText("text");
		Photo photo = new Photo(TestPhotos.extractLargePhoto());
		clientPage.addPhotos(Arrays.asList(photo), 0);
		client.createNewPage(clientPage, createPageCallback);
		Integer pageid = createPageCallback.get();
		BlockingCallback<Void> addPageCallback = new BlockingCallback<Void>();
		client.addPhoto(pageid, photo, addPageCallback, null);
		addPageCallback.get();

		/* verify server has the page */
		ServerJournal serverJournal = server.getModel().getJournal(docid);
		assertNotNull(serverJournal);
		List<ServerPage> pages = serverJournal.getPages();
		assertEquals(1, pages.size());
		ServerPage serverPage = pages.get(0);
		assertEquals(clientPage.getTitle(), serverPage.getTitle());
		assertEquals(clientPage.getHeadline(), serverPage.getHeadline());
		assertEquals(clientPage.getDate().toString(), serverPage.getDate());
		assertEquals(clientPage.getDistance(), serverPage.getDistance());
		assertEquals(clientPage.getText(), serverPage.getText());
		assertEquals(clientPage.isItalic(), serverPage.isItalic());
		assertEquals(clientPage.isBold(), serverPage.isBold());
		assertEquals(clientPage.getHeadingStyle(), serverPage.getHeadingStyle());
		assertEquals(clientPage.getIndent(), serverPage.getIndent());
	}

	@Test
	public void retryPhoto() {
	}
}
