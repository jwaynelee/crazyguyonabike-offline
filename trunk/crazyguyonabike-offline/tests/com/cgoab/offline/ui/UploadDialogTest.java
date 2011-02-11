package com.cgoab.offline.ui;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import testutils.TestLogSetup;

import com.cgoab.offline.client.mock.MockClient;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;

public class UploadDialogTest {

	static {
		TestLogSetup.configure();
	}

	UploadDialog dialog;
	Display display;

	public void createUpload(final List<Page> pages, final Journal journal) throws InterruptedException,
			TimeoutException {
		final CountDownLatch latch = new CountDownLatch(1);
		new Thread(new Runnable() {
			@Override
			public void run() {
				display = new Display();
				Shell s = new Shell(display);
				dialog = new UploadDialog(s);
				dialog.setUploadClient(new MockClient());
				dialog.setPages(pages);
				dialog.setJournal(journal);
				latch.countDown();
				try {
					dialog.open();
				} finally {
					display.dispose();
				}
			}
		}, "UI").start();
		if (!latch.await(5, TimeUnit.SECONDS)) {
			throw new TimeoutException();
		}
	}

	@After
	public void stop() {
		if (display != null && !display.isDisposed()) {
			display.syncExec(new Runnable() {
				@Override
				public void run() {
					display.dispose();
				}
			});
		}
	}

	@Test
	public void warningShownIfDocIdDifferent() throws Exception {
		Journal j = new Journal(null, "test");
		j.setDocIdHint(100); /* mock client doc_id = 100 | 200 */
		Page page1 = j.createNewPage();
		createUpload(Arrays.asList(page1), j);
		SWTBot bot = new SWTBot();
		bot.table().select(1);
		bot.button("Upload").click();

		/* expect warning to pop up */
		Assert.assertEquals("New document selected!", bot.activeShell().getText());

		bot.activeShell().bot().button("No").click();
	}
}
