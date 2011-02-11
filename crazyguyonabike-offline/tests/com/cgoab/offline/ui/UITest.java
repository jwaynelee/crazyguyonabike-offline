package com.cgoab.offline.ui;

import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotStyledText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import testutils.TestLogSetup;
import testutils.TestUtils;
import testutils.fakeserver.FakeCGOABModel.ServerJournal;
import testutils.fakeserver.FakeCGOABModel.ServerPage;
import testutils.fakeserver.FakeCGOABModel.ServerPhoto;
import testutils.fakeserver.FakeCGOABServer;
import testutils.photos.TestPhotos;

import com.cgoab.offline.client.PhotoUploadProgressListener;
import com.cgoab.offline.client.web.DefaultWebUploadClientFactory;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalXmlLoader;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Page.PhotosOrder;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.actions.UploadAction;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.ui.util.UIExecutor;
import com.cgoab.offline.util.Utils;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;
import com.cgoab.offline.util.resizer.ResizerService;

/**
 * High level UI integration tests, automated using SWTBot.
 */
public class UITest {

	private static final String MAIN_WINDOW_TITLE = "MAIN_WINDOW";
	private Display display;
	private MainWindow application;
	private CachingThumbnailProviderFactory thumbnailFactory;
	private ImageMagickResizerServiceFactory resizerFactory;
	private static FakeCGOABServer server;

	static {
		TestLogSetup.configure();
	}

	@BeforeClass
	public static void setupServer() throws Exception {
		server = new FakeCGOABServer(0);
	}

	@AfterClass
	public static void destroyServer() {
		if (server != null) {
			server.shutdown();
		}
	}

	Thread uiThread;

	@Before
	public void setupTest() throws Exception {
		server.getModel().createDefaultModel();
		PreferenceUtils.init();
		/*
		 * turn off (turn on then off as default is off and we don't update if
		 * we set to off!)
		 */
		PreferenceUtils.getStore().setValue(PreferenceUtils.CHECK_FOR_UPDATES, true);
		PreferenceUtils.getStore().setValue(PreferenceUtils.CHECK_FOR_UPDATES, false);
		final CountDownLatch latch = new CountDownLatch(1);
		uiThread = new Thread(new Runnable() {
			public void run() {
				display = new Display();
				application = new MainWindow(MAIN_WINDOW_TITLE);
				thumbnailFactory = new CachingThumbnailProviderFactory(display, ThumbnailViewer.RESIZE_STRATEGY,
						".thumbnails");
				resizerFactory = new ImageMagickResizerServiceFactory(display, ".images");
				application.setThumbnailProviderFactory(thumbnailFactory);
				application.setResizerServiceFactory(resizerFactory);
				DefaultWebUploadClientFactory factory = new DefaultWebUploadClientFactory();
				factory.setHost("localhost");
				factory.setPort(server.getHttpServer().getLocalPort());
				factory.setCallbackExecutor(new UIExecutor(display));
				/* don't use cookie store, complicates upload login wait */
				factory.setCookies(null);
				application.setUploadFactory(factory);
				latch.countDown();
				try {
					application.open();
				} finally {
					display.dispose();
				}
			}
		}, "UI");
		uiThread.start();

		if (!latch.await(1, TimeUnit.SECONDS)) {
			throw new TimeoutException();
		}
	}

	@After
	public void destroyUI() throws InterruptedException {
		PreferenceUtils.dispose();
		if (thumbnailFactory != null) {
			thumbnailFactory.dispose();
		}
		if (resizerFactory != null) {
			resizerFactory.dispose();
		}

		if (uiThread.isAlive()) {
			if (display != null && !display.isDisposed()) {
				display.syncExec(new Runnable() {
					@Override
					public void run() {
						display.dispose();
					}
				});
			}
			uiThread.join();
		}
	}

	/* Test for new feature */
	@Test
	public void uploadPagesOneAtATime() throws Exception {
		final Journal journal = new Journal(getTestJournalFilePath(), "Test");
		journal.createNewPage().setTitle("Page 1");
		journal.createNewPage().setTitle("Page 2");
		journal.createNewPage().setTitle("Page 3");
		journal.createNewPage().setTitle("Page 4");
		setCurrentJournal(journal);

		final SWTBot bot = new SWTBot();
		bot.menu("File").menu("Save Journal").click();

		/* upload page 1 first, then 2 & 3 together */
		SWTBotTreeItem[] pages = bot.tree().getAllItems()[0].getItems();
		assertEquals(4, pages.length);

		pages[0].select();
		pages[0].contextMenu("Upload Page").click();
		handleUploadStart(bot);
		bot.waitUntil(Conditions.shellIsActive("Upload completed"));
		bot.activeShell().bot().button("OK").click();
		ServerJournal j1 = server.getModel().getJournal(1);
		assertEquals(1, j1.getPages().size());
		assertEquals("Page 1", j1.getPages().get(0).getTitle());

		pages = bot.tree().getAllItems()[0].getItems();
		assertEquals(3, pages.length);

		bot.tree().select(pages[0], pages[1]).contextMenu("Upload Pages").click();
		handleUploadStart(bot);
		bot.waitUntil(Conditions.shellIsActive("Upload completed"));
		bot.activeShell().bot().button("OK").click();

		assertEquals(3, j1.getPages().size());
		assertEquals("Page 2", j1.getPages().get(1).getTitle());
		assertEquals("Page 3", j1.getPages().get(2).getTitle());

		pages = bot.tree().getAllItems()[0].getItems();
		assertEquals(1, pages.length);
		assertEquals("Page 4", pages[0].getText());
	}

	/* Test for BUG */
	@Test
	public void loadNewJournalWithMissingPhoto() throws Exception {
		final Journal journal = new Journal(getTestJournalFilePath(), "Test");
		Page page = journal.createNewPage();
		page.addPhotos(Arrays.asList(new Photo(new File("bogus.jpg"))), 0);

		/* inject journal in UI */
		setCurrentJournal(journal);

		/* when journal is loaded, UI should pop up with "File missing" */
		final SWTBot bot = new SWTBot();
		bot.waitUntil(Conditions.shellIsActive("Failed to load image"));
		bot.activeShell().bot().button("No").click(); /* discard */

		/* thumbnail view should be empty */
		final AtomicInteger sizeHolder = new AtomicInteger();
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				sizeHolder.set(application.thumbnailView.getViewer().getThumbnails().size());
			}
		});
		assertEquals(0, sizeHolder.get());
	}

	/**
	 * Performs main use-case of the application.
	 * 
	 * @throws Exception
	 */
	@Test
	public void createNewJournalAndUploadTest() throws Exception {
		final SWTBot bot = new SWTBot();

		/* 1) create a new journal */
		bot.menu("File").menu("New Journal").click();
		SWTBotShell newJournalShell = bot.activeShell();
		Assert.assertTrue(newJournalShell.getText().toLowerCase().contains("new journal"));
		newJournalShell.bot().checkBox("default").click();
		SWTBotText nameTextBox = newJournalShell.bot().textWithLabel("Name: ");
		String journalName = "TestJournal";
		nameTextBox.setText(journalName);
		SWTBotText locationTextBox = newJournalShell.bot().textWithLabel("Location: ");
		String journalLocation = getTestJournalFilePath().getAbsolutePath();
		locationTextBox.setText(journalLocation);
		newJournalShell.bot().button("OK").click();

		/* 2) create 2 new pages, each with 2 photos */
		bot.menu("File").menu("&New Page").click();
		// assertEquals(bot2.textWithLabel("Title:"), bot2.getFocusedWidget());
		bot.textWithLabel("Title:").setText("Day 1");
		bot.textWithLabel("Headline:").setText("to Plymouth");
		bot.textWithLabel("Distance:").setText("100");
		bot.dateTimeWithLabel("Date:").setDate(new Date(2011 - 1900, 0, 1));
		bot.styledText(0).setText("The first days text.");
		File photo1 = TestPhotos.extractLargePhoto();
		File photo2 = TestPhotos.extractLargePhoto();
		addPhotosToCurrentPage(bot, new File[] { photo1, photo2 });

		/* first time we've tried to add photos so answer questions */
		assertEquals("Use embedded thumnails?", bot.activeShell().getText());
		bot.activeShell().bot().button("No").click();
		assertEquals("Resize photos?", bot.activeShell().getText());
		bot.activeShell().bot().button("Yes").click();

		/* assert context menu in sync */
		assertTrue(bot.tree().contextMenu("Resize photos").isChecked());
		assertFalse(bot.tree().contextMenu("Use EXIF thumbnail").isChecked());

		bot.menu("File").menu("New Page").click();
		assertEquals("Day 2", bot.textWithLabel("Title:").getText());
		bot.textWithLabel("Headline:").setText("to Munich");
		bot.textWithLabel("Distance:").setText("50");
		assertEquals(new Date(2011 - 1900, 0, 2), bot.dateTimeWithLabel("Date:").getDate());
		bot.styledText(0).setText("The second days text.");
		File photo3 = TestPhotos.extractLargePhoto();
		File photo4 = TestPhotos.extractLargePhoto();
		addPhotosToCurrentPage(bot, new File[] { photo3, photo4 });

		bot.menu("File").menu("Save Journal").click();

		/* 3) save journal, check file */
		{
			Journal journalFromFile = JournalXmlLoader.open(new File(journalLocation));
			JournalXmlLoader.validateJournal(journalFromFile);
			assertEquals(journalLocation, journalFromFile.getFile().getAbsolutePath());
			assertEquals(journalName, journalFromFile.getName());
			assertEquals(2, journalFromFile.getPages().size());

			{
				Page page1 = journalFromFile.getPages().get(0);
				assertEquals("Day 1", page1.getTitle());
				assertEquals("to Plymouth", page1.getHeadline());
				assertEquals(100, page1.getDistance());
				assertEquals(2, page1.getPhotos().size());
				assertEquals(photo1, page1.getPhotos().get(0).getFile());
				assertEquals(photo2, page1.getPhotos().get(1).getFile());
				Page page2 = journalFromFile.getPages().get(1);
				assertEquals("Day 2", page2.getTitle());
				assertEquals("to Munich", page2.getHeadline());
				assertEquals(50, page2.getDistance());
				assertEquals(2, page2.getPhotos().size());
				assertEquals(photo3, page2.getPhotos().get(0).getFile());
				assertEquals(photo4, page2.getPhotos().get(1).getFile());
			}
		}

		/* 4) Upload */
		bot.tree().getAllItems()[0].select();
		bot.tree().contextMenu("Upload Journal").click();
		handleUploadStart(bot);

		bot.waitUntil(Conditions.shellIsActive("Upload completed"));
		bot.activeShell().bot().button("OK").click();

		/* 5) check tree is filtered */
		SWTBotTreeItem journalNode = bot.tree().getTreeItem(journalName);
		assertEquals(0, journalNode.getItems().length);

		/* 5a) show uploaded */
		journalNode.contextMenu("Hide uploaded").click();
		assertEquals(2, journalNode.getItems().length);

		/* 5b) check uploaded pages are now read-only */
		journalNode.getItems()[0].select();
		final SWTBotText title = bot.textWithLabel("Title:");
		final SWTBotStyledText text = bot.styledText(0);
		assertEquals("Day 1", title.getText());
		final AtomicBoolean titleEditable = new AtomicBoolean();
		final AtomicBoolean textEditable = new AtomicBoolean();
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				titleEditable.set(title.widget.getEditable());
				textEditable.set(text.widget.getEditable());
			}
		});
		assertFalse(titleEditable.get());
		assertFalse(textEditable.get());

		/* 5c) verify server model */
		Journal uiJournal = JournalSelectionService.getInstance().getCurrentJournal();
		ServerJournal serverJournal = server.getModel().getJournal(1);
		assertEquals(uiJournal.getDocIdHint(), serverJournal.getDocId());
		assertEquals(2, serverJournal.getPages().size());
		ServerPage serverPage1 = serverJournal.getPages().get(0);
		ServerPage serverPage2 = serverJournal.getPages().get(1);
		assertFalse(serverPage1.isVisible());
		assertFalse(serverPage2.isVisible());
		Page page1 = uiJournal.getPages().get(0);
		Page page2 = uiJournal.getPages().get(1);
		assertPagesEqual(page1, serverPage1);
		assertPagesEqual(page2, serverPage2);
		assertEquals(UploadState.UPLOADED, page1.getState());
		assertEquals(UploadState.UPLOADED, page2.getState());

		assertEquals(2, serverPage1.getPhotos().size());
		ServerPhoto serverPhoto1 = serverPage1.getPhotos().get(0);
		assertEquals(photo1.getName(), serverPhoto1.getFilename());

		/* verify the resized photo was sent */
		assertThat(serverPhoto1.getSize(), lessThan(photo1.length()));
		ResizerService service = (ResizerService) JournalSelectionService.getInstance().getCurrentJournal()
				.getData(ResizerService.KEY);
		assertEquals(service.getResizedPhotoFile(photo1).length(), serverPhoto1.getSize());
		assertEquals(2, serverPage2.getPhotos().size());

		/*
		 * 6) quit, save first otherwise we'll break bot as shell closes on
		 * mouse down
		 */
		bot.menu("File").menu("Save Journal").click();
		bot.menu("File").menu("Exit").click();

		/* 7) make sure journal saved */
		String path = PreferenceUtils.getStore().getString(PreferenceUtils.LAST_JOURNAL);
		assertEquals(uiJournal.getFile().getAbsolutePath(), path);
		assertTrue(display.isDisposed());
	}

	private void assertPagesEqual(Page page, ServerPage serverpage) {
		assertEquals(page.getTitle(), serverpage.getTitle());
		assertEquals(page.getHeadline(), page.getHeadline());
		assertEquals(page.isBold(), serverpage.isBold());
		assertEquals(page.isItalic(), serverpage.isItalic());
		assertEquals(page.getIndent(), serverpage.getIndent());
		assertEquals(page.getHeadingStyle(), serverpage.getHeadingStyle());
		assertEquals(page.getText(), serverpage.getText());
		assertEquals(page.getDate().toString(), serverpage.getDate());
		assertEquals(page.getDistance(), serverpage.getDistance());
		assertEquals(page.getFormat(), serverpage.getFormat());
		assertEquals(page.getServerId(), serverpage.getPageId());
	}

	private void handleUploadStart(SWTBot bot) {
		/*
		 * image resizer might still be working so we'll get the progress dialog
		 * so wait until upload opens.
		 */
		bot.waitUntil(Conditions.shellIsActive(UploadAction.SET_PAGES_VISIBLE_TITLE));
		bot.activeShell().bot().button("No").click();
		bot.waitUntil(new ShellNameMatches("Upload.*"));
		SWTBot uploadBot = bot.activeShell().bot();

		uploadBot.textWithLabel("Username:").setText("bob");
		uploadBot.textWithLabel("Password:").setText("secret");
		uploadBot.button("Login").click();
		uploadBot.waitUntil(new ICondition() {

			SWTBot bot;

			@Override
			public boolean test() throws Exception {
				return bot.table().rowCount() > 0;
			}

			@Override
			public void init(SWTBot bot) {
				this.bot = bot;
			}

			@Override
			public String getFailureMessage() {
				return null;
			}
		}, 5000);

		uploadBot.table().click(0, 0);
		uploadBot.button("Upload").click();

		/* wait until the all done box pops up */
	}

	@Test
	public void pageErrorUpload() throws Exception {
		Journal journal = new Journal(getTestJournalFilePath(), "test");
		Page page1 = journal.createNewPage();
		page1.setTitle("Day 1");
		page1.setDate(new LocalDate(2011, 01, 01));
		Page page2 = journal.createNewPage();
		page2.setTitle("Day 2");
		page2.setDate(new LocalDate(2011, 01, 02));
		setCurrentJournal(journal);

		/* configure server with single day */
		server.getModel().getJournal(1).addPage(new ServerPage(Collections.singletonMap("date", "2011-01-03")));

		SWTBot bot = new SWTBot();
		bot.tree().getAllItems()[0].select();
		bot.tree().contextMenu("Upload Journal").click();
		handleUploadStart(bot);

		bot.waitUntil(Conditions.shellIsActive("Error uploading"));

		assertEquals(UploadState.ERROR, page1.getState());
		assertEquals(UploadState.NEW, page2.getState());

		/* return to main window */
		bot.activeShell().bot().button("OK").click();
		bot.activeShell().bot().button("Cancel").click();

		/* check that only page 2 is selected in tree viewer */
		assertEquals(2, bot.tree().getTreeItem(journal.getName()).rowCount());
		assertEquals(page1.getTitle(), bot.textWithLabel("Title:").getText());
		bot.menu("File").menu("Save Journal").click();
		bot.menu("File").menu("Exit").click();
	}

	@Test
	public void retryPhotoUpload() throws Exception {
		Journal journal = new Journal(getTestJournalFilePath(), "test");
		File photo = TestPhotos.extractSmallPhoto();
		Page page = journal.createNewPage();
		page.addPhotos(Arrays.asList(new Photo(photo)), 0);
		setCurrentJournal(journal);

		/* 2 invocations per error */
		final CountDownLatch latch = new CountDownLatch(3 * 2);

		/* before upload make sure photo uploads will fail first 3 times */
		server.addPhotoUploadListener(new PhotoUploadProgressListener() {

			@Override
			public void uploadPhotoProgress(Photo photo, long bytes, long total) {
				float done = (float) bytes / total;
				if (latch.getCount() > 0 && done > 0.5) {
					latch.countDown();
					throw new RuntimeException("SimulateFailedUpload#" + latch.getCount());
				}
			}
		});

		/* upload */
		SWTBot bot = new SWTBot();
		bot.tree().getAllItems()[0].select();
		bot.tree().contextMenu("Upload Journal").click();
		handleUploadStart(bot);

		/* wait on latch (otherwise SWTBot might timeout) */
		assertEquals(true, latch.await(15, TimeUnit.SECONDS));

		/* expect complete */
		bot.waitUntil(Conditions.shellIsActive("Upload completed"));
		bot.activeShell().bot().button("OK").click();
		bot.menu("File").menu("Exit").click();
	}

	private void setCurrentJournal(final Journal journal) {
		/* bind into UI */
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				JournalSelectionService.getInstance().setJournal(journal);
			}
		});
	}

	@Test
	public void photoSortOrder() throws Exception {
		final Journal testJournal = new Journal(getTestJournalFilePath(), "test");
		File extractedPhoto = TestPhotos.extractSmallPhoto();
		Photo photo1 = new Photo(new File(extractedPhoto.getParent() + File.separator + "P100.jpg"));
		Photo photo2 = new Photo(new File(extractedPhoto.getParent() + File.separator + "P101.jpg"));
		Photo photo3 = new Photo(new File(extractedPhoto.getParent() + File.separator + "P102.jpg"));
		Utils.copyFile(extractedPhoto, photo1.getFile());
		Utils.copyFile(extractedPhoto, photo2.getFile());
		Utils.copyFile(extractedPhoto, photo3.getFile());

		final Page page = testJournal.createNewPage();
		page.setPhotosOrder(PhotosOrder.NAME);
		/* add out of order */
		page.addPhotos(Arrays.asList(photo2, photo1, photo3), 0);

		setCurrentJournal(testJournal);

		/* sanity check they are returned from journal in NAME order */
		assertEquals(Arrays.asList(photo1, photo2, photo3), page.getPhotos());
		assertEquals(Arrays.asList(photo1, photo2, photo3), getThumbnailViewerObjects());

		SWTBot bot = new SWTBot();
		assertTrue(bot.radio("name").isSelected());
		assertFalse(bot.radio("manual").isSelected());

		/* simulate moving 3rd photo back one spot */
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				/* manually move */
				try {
					page.movePhotos(Arrays.asList(page.getPhotos().get(2)), 1);
					application.thumbnailView.getViewer().refresh();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});

		/* verify radio was updated */
		assertFalse(bot.radio("name").isSelected());
		assertTrue(bot.radio("manual").isSelected());

		/* sanity check they are returned from journal in MANUAL order */
		assertEquals(Arrays.asList(photo1, photo3, photo2), page.getPhotos());
		assertEquals(Arrays.asList(photo1, photo3, photo2), getThumbnailViewerObjects());
		assertEquals(PhotosOrder.MANUAL, page.getPhotosOrder());

		/* click on NAME radio */
		bot.radio("name").click();

		/* verify radio was updated */
		assertTrue(bot.radio("name").isSelected());
		assertFalse(bot.radio("manual").isSelected());

		/* should be back in order */
		assertEquals(Arrays.asList(photo1, photo2, photo3), page.getPhotos());
		assertEquals(Arrays.asList(photo1, photo2, photo3), getThumbnailViewerObjects());
		assertEquals(PhotosOrder.NAME, page.getPhotosOrder());
	}

	private List<Object> getThumbnailViewerObjects() {
		final AtomicReference<List<Object>> h = new AtomicReference<List<Object>>();
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				h.set(application.thumbnailView.getViewer().getThumbnails());
			}
		});
		return h.get();
	}

	private File getTestJournalFilePath() throws IOException {
		return new File(TestUtils.getTestTempDirectory() + File.separator + "journal.xml");
	}

	/**
	 * Can't do DND, can't open native dialog so just call directly into
	 * implementation (yuck)
	 */
	private void addPhotosToCurrentPage(SWTBot bot, final File[] files) {
		assertEquals(1, bot.tree().selectionCount());
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				application.addPhotosAction.run(files);
			}
		});
	}

	public void loadJournalTest() {
		/* can't test as we do not have access to native dialog */
	}

	public void addPhotosToJournal() {
		/* can't test DND yet */
	}

	static class ShellNameMatches implements ICondition {

		SWTBot bot;
		String pattern;

		public ShellNameMatches(String pattern) {
			this.pattern = pattern;
		}

		@Override
		public boolean test() throws Exception {
			return bot.activeShell().getText().matches(pattern);
		}

		@Override
		public void init(SWTBot bot) {
			this.bot = bot;
		}

		@Override
		public String getFailureMessage() {
			return null;
		}

	}
}