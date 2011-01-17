package com.cgoab.offline.ui.thumbnailviewer;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import testutils.photos.TestPhotos;

import com.cgoab.offline.Application;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;
import com.cgoab.offline.util.ListenableThreadPoolExecutor;

public class CachingThumbnailProviderTest {

	ExecutorService executor;
	Display display;

	@Before
	public void createExecutor() throws TimeoutException {
		executor = new ListenableThreadPoolExecutor("TEST", 1, Thread.NORM_PRIORITY);
		display = new Display();
	}

	@After
	public void destroyExecutor() {
		executor.shutdownNow();
		display.dispose();
	}

	@Test
	public void testCreateSingleThumbnail() throws Exception {
		File dir = new File(System.getProperty("java.io.tmpdir") + File.separator + "thumbs");
		final CachingThumbnailProvider p = new CachingThumbnailProvider(executor, dir, display,
				Application.THUMBNAIL_RESIZER);
		final File source = TestPhotos.getPhotoAsTempFile();
		// needs a looping display
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				Future<Thumbnail> result;
				result = p.get(source, null, null);
				Thumbnail thumbnail;
				try {
					thumbnail = result.get();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				Assert.assertNotNull(thumbnail.imageData);
				Image img = new Image(display, thumbnail.imageData);
				Rectangle bounds = img.getBounds();
				Assert.assertThat(bounds.x, is(lessThan(ThumbnailViewer.THUMBNAIL_WIDTH)));
				Assert.assertThat(bounds.y, is(lessThan(ThumbnailViewer.THUMBNAIL_HEIGHT)));
				// TODO check that a repeated get is quicker
				display.dispose();
			}
		});

		loop(display);
	}

	private static void loop(Display display) {
		while (!display.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
}
