package com.cgoab.offline.ui.thumbnailviewer;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.BasicConfigurator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import testutils.photos.TestPhotos;

import com.cgoab.offline.Application;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;
import com.cgoab.offline.util.FutureCompletionListener;
import com.cgoab.offline.util.JobListener;
import com.cgoab.offline.util.ListenableThreadPoolExecutor;

public class CachingThumbnailProviderTest {

	static {
		BasicConfigurator.configure();
	}

	private ExecutorService executor;
	private DisplayLoop loop;
	private CachingThumbnailProviderFactory factory;

	@Before
	public void create() throws Exception {
		executor = new ListenableThreadPoolExecutor(getClass().getName(), 1, Thread.NORM_PRIORITY);
		loop = new DisplayLoop();
		factory = new CachingThumbnailProviderFactory(loop.getDisplay(), Application.THUMBNAIL_RESIZER, ".thumbs");
	}

	@After
	public void destroy() {
		executor.shutdownNow();
		loop.dispose();
	}

	@Test
	public void createThumbnail() throws Exception {
		/* ${tmp.dir}/CachingThumbnailProviderTest/01234567/test.thumbs */
		String tmpDir = System.getProperty("java.io.tmpdir");
		String journalLocation = tmpDir + File.separator + getClass().getName() + File.separator
				+ System.currentTimeMillis() + File.separator + "test";
		Journal journal = new Journal(new File(journalLocation), "test");
		final CachingThumbnailProvider provider = factory.createThumbnailProvider(journal);
		final File source = TestPhotos.getPhotoAsTempFile();

		final Map<Class<?>, Object> results = new HashMap<Class<?>, Object>();
		final CountDownLatch latch = new CountDownLatch(1);

		final FutureCompletionListener<Thumbnail> listener = new FutureCompletionListener<ThumbnailProvider.Thumbnail>() {
			@Override
			public void onCompletion(Future<Thumbnail> result, Object data) {
				results.put(Future.class, result);
				results.put(Object.class, data);
				results.put(Thread.class, Thread.currentThread());
				latch.countDown();
			}
		};

		Mockery mock = new Mockery();
		final JobListener jobListener = mock.mock(JobListener.class);
		mock.checking(new Expectations() {
			{
				/* check thread? */
				oneOf(jobListener).update(1);
				oneOf(jobListener).update(0);
			}
		});
		provider.addJobListener(jobListener);

		final Object data = "foo";
		loop.getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				/* create */
				provider.get(source, listener, data);
			}
		});

		if (!latch.await(5, TimeUnit.SECONDS)) {
			throw new TimeoutException();
		}

		assertEquals(loop.getDisplay().getThread(), results.get(Thread.class));
		assertEquals(data, results.get(Object.class));
		@SuppressWarnings("unchecked")
		Future<Thumbnail> future = (Future<Thumbnail>) results.get(Future.class);
		assertTrue(future.isDone());
		Thumbnail thumbnail = future.get();
		assertNotNull(thumbnail.imageData);
		Image img = new Image(loop.getDisplay(), thumbnail.imageData);
		Rectangle bounds = img.getBounds();
		assertThat(bounds.x, is(lessThan(ThumbnailViewer.THUMBNAIL_WIDTH)));
		assertThat(bounds.y, is(lessThan(ThumbnailViewer.THUMBNAIL_HEIGHT)));

		mock.assertIsSatisfied();
		// /* make sure image was written to disk */
		// File inCache = provider.getNameInCache(source);
		// assertTrue(inCache.exists());
	}

	public static class DisplayLoop {
		Display display;

		public DisplayLoop() throws InterruptedException, BrokenBarrierException, TimeoutException {
			final CyclicBarrier barrier = new CyclicBarrier(2);
			new Thread(new Runnable() {
				@Override
				public void run() {
					display = new Display();
					try {
						barrier.await();
						while (!display.isDisposed()) {
							if (!display.readAndDispatch()) {
								display.sleep();
							}
						}
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			}, "ui-thread").start();
			barrier.await(5, TimeUnit.SECONDS);
		}

		public void dispose() {
			if (!display.isDisposed()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						display.dispose();
					}
				});
			}
		}

		public Display getDisplay() {
			return display;
		}
	}
}
