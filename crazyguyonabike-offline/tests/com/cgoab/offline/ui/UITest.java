package com.cgoab.offline.ui;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import org.apache.log4j.BasicConfigurator;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalXmlLoader;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.ui.thumbnailviewer.FitWithinResizeStrategy;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;

/**
 * High level UI integration tests.
 */
public class UITest {

	private Display display;
	private Shell rootShell;
	private ApplicationWindow editor;
	private CachingThumbnailProviderFactory thumbnailFactory;

	static {
		BasicConfigurator.configure();
	}

	@Before
	public void createUI() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		new Thread(new Runnable() {
			public void run() {
				Display display = new Display();
				rootShell = new Shell(display);
				editor = new ApplicationWindow(rootShell);
				editor.setPreferences(new Preferences());
				FitWithinResizeStrategy resizer = new FitWithinResizeStrategy(new Point(
						ThumbnailViewer.THUMBNAIL_WIDTH, ThumbnailViewer.THUMBNAIL_HEIGHT));
				thumbnailFactory = new CachingThumbnailProviderFactory(display, resizer, ".thumbnails");
				editor.setThumbnailProviderFactory(thumbnailFactory);
				latch.countDown();
				try {
					editor.open();
				} finally {
					rootShell.dispose();
					display.dispose();
				}
			}
		}, "UI").start();
		latch.await();
	}

	public void destroyUI() {
		if (thumbnailFactory != null) {
			thumbnailFactory.dispose();
		}
		if (rootShell != null && !rootShell.isDisposed()) {
			rootShell.dispose();
		}
		if (display != null && !display.isDisposed()) {
			display.dispose();
		}
	}

	@Test
	public void createNewJournalTest() throws Exception {
		final SWTBot bot = new SWTBot(rootShell);
		bot.menu("File").menu("New Journal").click();
		SWTBotShell newJournalShell = bot.activeShell();
		Assert.assertTrue(newJournalShell.getText().toLowerCase().contains("new journal"));
		newJournalShell.bot().checkBox("default").click();
		SWTBotText nameTextBox = newJournalShell.bot().textWithLabel("Name: ");
		String journalName = "TEST";
		nameTextBox.setText(journalName);
		SWTBotText locationTextBox = newJournalShell.bot().textWithLabel("Location: ");
		String tempDir = System.getProperty("java.io.tmpdir");
		File journalLocation = new File(tempDir + File.separator + "cgoabofflinetest." + System.currentTimeMillis()
				+ ".xml");
		locationTextBox.setText(journalLocation.getAbsolutePath());
		newJournalShell.bot().button("OK").click();

		// create 2 new pages
		bot.menu("File").menu("New Page").click();
		bot.menu("File").menu("New Page").click();
		bot.menu("File").menu("Save").click();
		// re-load model from file; check it has 1 journal with 2 pages
		Journal journal = JournalXmlLoader.open(journalLocation);
		JournalXmlLoader.validateJournal(journal);
		Assert.assertEquals(journalLocation, journal.getFile());
		Assert.assertEquals(journalName, journal.getName());
		Assert.assertEquals(2, journal.getPages().size());
		journalLocation.deleteOnExit();
	}

	@Test
	public void loadJournalTest() {
		/* can't test as we do not have access to native dialog */
	}

	public void addPhotosToJournal() {
		/* can't test DND yet */
	}
}