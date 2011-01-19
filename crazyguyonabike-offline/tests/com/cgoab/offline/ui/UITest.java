package com.cgoab.offline.ui;

import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.impl.client.BasicCookieStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.hamcrest.Matchers;
import org.htmlcleaner.XPatherException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import testutils.TestLogSetup;
import testutils.TestUtils;
import testutils.fakeserver.FakeCGOABServer;
import testutils.fakeserver.ServerModel.ServerJournal;
import testutils.fakeserver.ServerModel.ServerPage;
import testutils.fakeserver.ServerModel.ServerPhoto;
import testutils.photos.TestPhotos;

import com.cgoab.offline.client.web.DefaultWebUploadClientFactory;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalXmlLoader;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.ui.util.UIExecutor;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;
import com.cgoab.offline.util.resizer.ResizerService;

/**
 * High level UI integration tests.
 */
public class UITest {

	private Display display;
	private MainWindow application;
	private CachingThumbnailProviderFactory thumbnailFactory;
	private ImageMagickResizerServiceFactory resizerFactory;
	private static FakeCGOABServer server;

	static {
		TestLogSetup.configure();
	}

	@BeforeClass
	public static void setupServer() throws IOException, XPatherException {
		server = new FakeCGOABServer(0);
	}

	@AfterClass
	public static void destroyServer() {
		if (server != null) {
			server.shutdown();
		}
	}

	@Before
	public void setup() throws InterruptedException, TimeoutException {
		final CountDownLatch latch = new CountDownLatch(1);
		new Thread(new Runnable() {

			public void run() {
				display = new Display();
				application = new MainWindow();
				application.setPreferences(new Preferences());
				thumbnailFactory = new CachingThumbnailProviderFactory(display, ThumbnailViewer.RESIZE_STRATEGY,
						".thumbnails");
				resizerFactory = new ImageMagickResizerServiceFactory(display, ".images");
				application.setThumbnailProviderFactory(thumbnailFactory);
				application.setResizerServiceFactory(resizerFactory);
				DefaultWebUploadClientFactory factory = new DefaultWebUploadClientFactory();
				factory.setHost("localhost");
				factory.setPort(server.getHttpServer().getLocalPort());
				factory.setCallbackExecutor(new UIExecutor(display));
				factory.setCookies(new BasicCookieStore());
				application.setUploadFactory(factory);
				latch.countDown();
				try {
					application.open();
				} finally {
					display.dispose();
				}
			}
		}, "UI").start();

		if (!latch.await(1, TimeUnit.SECONDS)) {
			throw new TimeoutException();
		}
	}

	@After
	public void destroyUI() {
		if (thumbnailFactory != null) {
			thumbnailFactory.dispose();
		}
		if (resizerFactory != null) {
			resizerFactory.dispose();
		}
		// if (display != null && !display.isDisposed()) {
		// display.syncExec(new Runnable() {
		// @Override
		// public void run() {
		// display.dispose();
		// }
		// });
		// }
	}

	/**
	 * Performs main use-case of the application.
	 * 
	 * @throws Exception
	 */
	@Test
	public void createNewJournalAndUploadTest() throws Exception {
		final SWTBot bot = new SWTBot(application.getShell());

		/* 1) create a new journal */
		bot.menu("File").menu("New Journal").click();
		SWTBotShell newJournalShell = bot.activeShell();
		Assert.assertTrue(newJournalShell.getText().toLowerCase().contains("new journal"));
		newJournalShell.bot().checkBox("default").click();
		SWTBotText nameTextBox = newJournalShell.bot().textWithLabel("Name: ");
		String journalName = "TestJournal";
		nameTextBox.setText(journalName);
		SWTBotText locationTextBox = newJournalShell.bot().textWithLabel("Location: ");
		String journalLocation = TestUtils.getTestTempDirectory().getAbsolutePath() + File.separator + "journal.xml";
		locationTextBox.setText(journalLocation);
		newJournalShell.bot().button("OK").click();

		/* 2) create 2 new pages, each with 2 photos */
		bot.menu("File").menu("&New Page").click();
		SWTBot bot2 = bot.activeShell().bot();
		// assertEquals(bot2.textWithLabel("Title:"), bot2.getFocusedWidget());
		bot2.textWithLabel("Title:").setText("Day 1");
		bot2.textWithLabel("Headline:").setText("to Plymouth");
		bot2.textWithLabel("Distance:").setText("100");
		bot2.dateTimeWithLabel("Date:").setDate(new Date(2011 - 1900, 0, 1));
		bot2.styledText(0).setText("The first days text.");
		File photo1 = TestPhotos.extractLargePhoto();
		File photo2 = TestPhotos.extractLargePhoto();
		addPhotosToCurrentPage(bot2, new File[] { photo1, photo2 });

		/* first time we've tried to add photos so answer questions */
		assertEquals("Use embedded thumnails?", bot2.activeShell().getText());
		bot2.activeShell().bot().button("No").click();
		assertEquals("Resize photos?", bot2.activeShell().getText());
		bot2.activeShell().bot().button("Yes").click();

		/* assert context menu in sync */
		assertTrue(bot2.tree().contextMenu("Resize photos").isChecked());
		assertFalse(bot2.tree().contextMenu("Use EXIF thumbnail").isChecked());

		bot.menu("File").menu("New Page").click();
		assertEquals("Day 2", bot2.textWithLabel("Title:").getText());
		bot2.textWithLabel("Headline:").setText("to Munich");
		bot2.textWithLabel("Distance:").setText("50");
		assertEquals(new Date(2011 - 1900, 0, 2), bot2.dateTimeWithLabel("Date:").getDate());
		bot2.styledText(0).setText("The second days text.");
		File photo3 = TestPhotos.extractLargePhoto();
		File photo4 = TestPhotos.extractLargePhoto();
		addPhotosToCurrentPage(bot2, new File[] { photo3, photo4 });

		bot.menu("File").menu("Save Journal").click();

		/* 3) save journal, check file */
		Journal journal = JournalXmlLoader.open(new File(journalLocation));
		JournalXmlLoader.validateJournal(journal);
		assertEquals(journalLocation, journal.getFile().getAbsolutePath());
		assertEquals(journalName, journal.getName());
		assertEquals(2, journal.getPages().size());

		{
			Page page1 = journal.getPages().get(0);
			assertEquals("Day 1", page1.getTitle());
			assertEquals("to Plymouth", page1.getHeadline());
			assertEquals(100, page1.getDistance());
			assertEquals(2, page1.getPhotos().size());
			assertEquals(photo1, page1.getPhotos().get(0).getFile());
			assertEquals(photo2, page1.getPhotos().get(1).getFile());
			Page page2 = journal.getPages().get(1);
			assertEquals("Day 2", page2.getTitle());
			assertEquals("to Munich", page2.getHeadline());
			assertEquals(50, page2.getDistance());
			assertEquals(2, page2.getPhotos().size());
			assertEquals(photo3, page2.getPhotos().get(0).getFile());
			assertEquals(photo4, page2.getPhotos().get(1).getFile());
		}

		/* 4) Upload */
		bot2.tree().contextMenu("Upload Journal").click();

		/*
		 * image resizer might still be working so we'll get the progress dialog
		 * so wait until upload opens.
		 */
		bot2.waitUntil(Conditions.shellIsActive("Make new pages visible?"));
		bot2.activeShell().bot().button("No").click();

		SWTBot uploadBot = bot.activeShell().bot();
		assertTrue(uploadBot.activeShell().getText().startsWith("Upload"));
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
		bot.waitUntil(Conditions.shellIsActive("Upload completed"));
		bot.activeShell().bot().button("OK").click();

		/* 5) check tree is filtered */
		SWTBotTreeItem journalNode = bot2.tree().getTreeItem(journal.getName());
		assertEquals(0, journalNode.getItems().length);

		/* 5a) show uploaded */
		journalNode.contextMenu("Hide uploaded").click();
		assertEquals(2, journalNode.getItems().length);

		/* 5b) TODO check uploaded is read-only */
		journalNode.getItems()[0].select();
		final SWTBotText title = bot2.textWithLabel("Title:");
		assertEquals("Day 1", title.getText());
		final AtomicBoolean editable = new AtomicBoolean();
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				editable.set(title.widget.getEditable());
			}
		});
		assertFalse(editable.get());

		ServerJournal serverJournal = server.getModel().getJournal(1);
		assertEquals(2, serverJournal.getPages().size());
		ServerPage serverPage1 = serverJournal.getPages().get(0);
		assertFalse(serverPage1.isVisible());
		ServerPage serverPage2 = serverJournal.getPages().get(1);
		assertFalse(serverPage2.isVisible());
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
		String path = application.getPreferences().getValue(MainWindow.OPENJOURNALS_PREFERENCE_PATH);
		assertEquals(journal.getFile().getAbsolutePath(), path);
		assertTrue(display.isDisposed());
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