package com.cgoab.offline.client.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.impl.client.BasicCookieStore;
import org.apache.log4j.Level;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import testutils.TestProperties;
import testutils.fakeserver.FakeCGOABServer;
import testutils.fakeserver.ServerModel.ServerJournal;
import testutils.fakeserver.ServerModel.ServerPage;
import testutils.photos.TestPhotos;

import com.cgoab.offline.client.CompletionCallback;
import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.web.DefaultWebUploadClient.ServerOperationException;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.Page.HeadingStyle;
import com.cgoab.offline.util.NewFileAppender;

/**
 * Integration test using a "fake" HTTP server.
 */
public class DefaultWebUploadClientTest {

	DefaultWebUploadClient client;
	static FakeCGOABServer server;

	static {
		org.apache.log4j.Logger.getLogger("org.apache.http.wire").setLevel(Level.WARN);
	}

	@BeforeClass
	public static void create() throws Exception {
		server = new FakeCGOABServer(0);
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
		Callback<String> completion = new Callback<String>();
		client.login("bob", "secret", completion);
		String result = completion.get();
		assertEquals("bob", result);
		assertEquals("bob", client.getCurrentUsername());
		assertEquals("Billy Bob", client.getCurrentUserRealName());
	}

	@Test
	public void loginFails() throws Throwable {
		Callback<String> completion = new Callback<String>();
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
	public void getDocuments() throws Throwable {
		/* login */
		Callback<String> loginCompletion = new Callback<String>();
		client.login("bob", "secret", loginCompletion);
		assertEquals("bob", loginCompletion.get());

		/* get documents */
		Callback<List<DocumentDescription>> completion = new Callback<List<DocumentDescription>>();
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
		Callback<String> loginCompletion = new Callback<String>();
		client.login("bob", "secret", loginCompletion);
		assertEquals("bob", loginCompletion.get());

		/* get documents */
		Callback<List<DocumentDescription>> completion = new Callback<List<DocumentDescription>>();
		client.getDocuments(completion);
		List<DocumentDescription> documents = completion.get();

		int docid = documents.get(0).getDocumentId();

		/* init the client */
		Callback<Void> initCallback = new Callback<Void>();
		client.initialize(docid, initCallback);
		initCallback.get();

		/* add page & photo */
		Callback<Integer> createPageCallback = new Callback<Integer>();
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
		Photo photo = new Photo(TestPhotos.getPhotoAsTempFile());
		clientPage.addPhotos(Arrays.asList(photo), 0);
		client.createNewPage(clientPage, createPageCallback);
		Integer pageid = createPageCallback.get();
		Callback<Void> addPageCallback = new Callback<Void>();
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

	static class Callback<T> implements CompletionCallback<T> {

		private static final Logger LOG = LoggerFactory.getLogger(DefaultWebUploadClientTest.class);

		private CountDownLatch latch = new CountDownLatch(1);

		T result;

		Throwable exception;

		public T get() throws Throwable {
			if (TestProperties.waitForever) {
				latch.await();
			} else {
				int loops = 0;
				int timePerLoop = 5;
				while (!latch.await(timePerLoop, TimeUnit.SECONDS)) {
					loops++;
					LOG.info("Waited {}s for completion", loops * timePerLoop);
					if (loops > 10) {
						throw new TimeoutException();
					}
				}
			}

			if (exception != null) {
				throw exception;
			}
			return result;
		}

		@Override
		public void onCompletion(T result) {
			this.result = result;
			latch.countDown();
		}

		@Override
		public void onError(Throwable exception) {
			this.exception = exception;
			latch.countDown();
		}

		@Override
		public void retryNotify(Throwable exception, int retryCount) {
			/* ignore */
		}
	}

	static class CallingThreadExecutor implements Executor {
		@Override
		public void execute(Runnable command) {
			command.run();
		}
	}
}
