package com.cgoab.offline.util.resizer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Photo;
import com.cgoab.offline.ui.util.SWTUtils;
import com.cgoab.offline.ui.util.UIExecutor;
import com.cgoab.offline.util.FutureCompletionListener;
import com.cgoab.offline.util.JobListener;

public class ImageMagikResizerService implements FutureCompletionListener<Object>, ResizerService {

	private static final Logger LOG = LoggerFactory.getLogger(ImageMagikResizerService.class);

	/*
	 * File limit above which we will do the check for existing resized photo on
	 * a background thread.
	 */
	// TODO tune
	private static final int RESIZE_PHOTOS_SYNCHRONOUS_LIMIT = 10;

	// modified by UI thread only (so not locked)
	private final Map<Photo, Future<Object>> tasks = new HashMap<Photo, Future<Object>>(32);

	private final List<JobListener> listeners = new ArrayList<JobListener>();

	private final File photoFolder;

	private final ExecutorService taskExecutor;

	private final Executor uiExecutor;

	private final String magickPath;

	public ImageMagikResizerService(File photoFolder, ExecutorService taskExecutor, Display display, String magickPath) {
		this.photoFolder = photoFolder;
		this.taskExecutor = taskExecutor;
		this.uiExecutor = new UIExecutor(display);
		this.magickPath = magickPath;
	}

	@Override
	public File getResizedPhotoFile(File origional) {
		File f = getFileInCache(origional);
		if (f.exists()) {
			return f;
		}
		return null;
	}

	@Override
	public File getPhotoFolder() {
		return photoFolder;
	}

	private boolean hasJpegExtension(File f) {
		String name = f.getName().toLowerCase();
		return name.endsWith(".jpg") || name.endsWith(".jpeg");
	}

	@Override
	public long purge() {
		long bytes = 0;
		for (File f : photoFolder.listFiles()) {
			if (hasJpegExtension(f)) {
				bytes += f.length();
				if (!f.delete()) {
					LOG.debug("Failed to delete resized photo {}", f.getName());
				}
			}
		}
		return bytes;
	}

	@Override
	public void addJobListener(JobListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	@Override
	public void removeJobListener(JobListener listener) {
		listeners.remove(listener);
	}

	private File getFileInCache(File source) {
		return new File(photoFolder + File.separator + source.getName());
	}

	@Override
	public void resizeAll(List<Photo> photos) {
		SWTUtils.assertOnUIThread();
		// 1) already doing it or not eligible for resizing (only jpegs)
		for (Photo photo : photos) {
			if (tasks.containsKey(photo)) {
				continue;
			}
			if (!hasJpegExtension(photo.getFile())) {
				LOG.debug("Ignoring image [{}] as it does not end '.jpeg' or '.jpg'", photo.getFile().getAbsolutePath());
				continue;
			}
		}

		// 2) run a new task to check each file and resize as needed...
		CheckCacheAndResizePhotosTask resizeTask = new CheckCacheAndResizePhotosTask(photos);
		if (photos.size() > RESIZE_PHOTOS_SYNCHRONOUS_LIMIT) {
			taskExecutor.submit(resizeTask);
		} else {
			// do on calling thread...
			resizeTask.call();
		}
	}

	@Override
	public int activeTasks() {
		SWTUtils.assertOnUIThread();
		return tasks.size();
	}

	private void fireUpdate() {
		int ntasks = tasks.size();
		for (JobListener listener : listeners) {
			listener.update(ntasks);
		}
	}

	@Override
	public void onCompletion(final Future<Object> result, final Object data) {
		uiExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					result.get();
				} catch (InterruptedException e) {
					/* ignore */
				} catch (CancellationException e) {
					/* ignore */
				} catch (ExecutionException e) {
					// log here otherwise we'll miss the exception
					LOG.warn("Failed to resize image", e);
				}
				tasks.remove((Photo) data);
				fireUpdate();
			}
		});
	}

	@Override
	public void cancelAll() {
		SWTUtils.assertOnUIThread();
		for (Entry<Photo, Future<Object>> e : new HashSet<Entry<Photo, Future<Object>>>(tasks.entrySet())) {
			e.getValue().cancel(true);
		}
	}

	@Override
	public void removeAll(List<Photo> photos) {
		SWTUtils.assertOnUIThread();
		// cancel any pending resize jobs for these photos, then remove files
		for (Photo photo : photos) {
			Future<Object> job = tasks.get(photo);
			if (job != null) {
				job.cancel(true);
				try {
					/* wait for completion */
					job.get();
				} catch (Exception e) {
					/* ignore */
				}
			}

			File file = getFileInCache(photo.getFile());
			if (file.exists()) {
				file.delete();
			}
		}
		fireUpdate();
	}

	private class CheckCacheAndResizePhotosTask implements Callable<Object> {

		private final List<Photo> photos;

		public CheckCacheAndResizePhotosTask(List<Photo> photos) {
			this.photos = photos;
		}

		@Override
		public Object call() {
			final List<Photo> filesToResize = new ArrayList<Photo>();
			for (Photo photo : photos) {
				File sourceFile = photo.getFile();
				File targetFile = getFileInCache(sourceFile);
				if (targetFile.exists()) {
					if (targetFile.lastModified() >= sourceFile.lastModified()) {
						// already resized
						continue;
					}
					// delete the old resized image and start again
					targetFile.delete();
				}
				filesToResize.add(photo);
			}

			// 3) start new jobs to resize the photos
			uiExecutor.execute(new Runnable() {
				@Override
				public void run() {
					addResizeTasks(filesToResize);
				}
			});
			return null;
		}

		private void addResizeTasks(List<Photo> files) {
			SWTUtils.assertOnUIThread();
			for (Photo photo : files) {
				File sourceFile = photo.getFile();
				File targetFile = getFileInCache(sourceFile);
				ImageMagickResizeTask task = new ImageMagickResizeTask(magickPath, sourceFile, targetFile,
						ImageMagikResizerService.this, photo);
				tasks.put(photo, taskExecutor.submit(task));
				fireUpdate();
			}
		}
	}
}