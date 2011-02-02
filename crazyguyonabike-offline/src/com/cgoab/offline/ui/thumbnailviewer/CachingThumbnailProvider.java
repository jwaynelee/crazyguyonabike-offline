package com.cgoab.offline.ui.thumbnailviewer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;
import com.cgoab.offline.ui.util.SWTUtils;
import com.cgoab.offline.util.CompletedFuture;
import com.cgoab.offline.util.FutureCompletionListener;
import com.cgoab.offline.util.JobListener;
import com.cgoab.offline.util.ListenableCancellableTask;
import com.cgoab.offline.util.OS;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifDirectory;
import com.drew.metadata.jpeg.JpegDirectory;

/**
 * Thumbnail provider that caches images both on disk (all thumbnails) and in
 * memory (recently accessed thumbnails).
 */
public class CachingThumbnailProvider implements ThumbnailProvider, FutureCompletionListener<Thumbnail> {

	private static Logger LOG = LoggerFactory.getLogger(ThumbnailProvider.class);

	private static final String THUMBNAIL_EXTENSION = ".png";

	private final ThumbnailCache cache;

	private final File cacheDirectory;

	private final Display display;

	private final ExecutorService executor;

	private final List<JobListener> listeners = new ArrayList<JobListener>();

	private final ResizeStrategy resizer;

	private final Map<File, Future<Thumbnail>> tasks = new HashMap<File, Future<Thumbnail>>();

	private boolean useExifThumbnail = true;

	public CachingThumbnailProvider(ExecutorService executor, File cacheDirectory, Display display,
			ResizeStrategy resizer) {
		this.cacheDirectory = cacheDirectory;
		if (!cacheDirectory.exists()) {
			if (!cacheDirectory.mkdir()) {
				throw new IllegalStateException("Could not create thumbnail directory [" + cacheDirectory + "]");
			}
		} else if (!cacheDirectory.isDirectory()) {
			throw new IllegalArgumentException("[" + cacheDirectory + "] is not a directory");
		}
		this.executor = executor;
		this.display = display;
		this.cache = new ThumbnailCache(16 * 1024 * 1024); // 16mb
		this.resizer = resizer;
	}

	public void addJobListener(JobListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	/**
	 * Free up resources
	 */
	public void close() {
		SWTUtils.assertOnUIThread();

		// cancel any pending tasks
		if (tasks.size() > 0) {
			for (Future<Thumbnail> f : tasks.values()) {
				f.cancel(true);
			}
		}

		cache.clear();
	}

	private void fireStatusChanged() {
		for (JobListener listener : listeners) {
			listener.update(tasks.size());
		}
	}

	@Override
	public Future<Thumbnail> get(File source, FutureCompletionListener<Thumbnail> listener, Object data) {
		SWTUtils.assertOnUIThread();
		LOG.debug("Request for thumbnail of [{}]", source.getAbsolutePath());

		// 0) file does not exist (or was deleted)
		if (!source.exists()) {
			LOG.debug("Source image does not exist, removing from cache(s)");
			cache.remove(source);
			File f = getNameInCache(source);
			if (f.exists()) {
				f.delete();
			}
			CompletedFuture<Thumbnail> result = new CompletedFuture<Thumbnail>(null, new FileNotFoundException(
					source.getAbsolutePath()));
			if (listener != null) {
				listener.onCompletion(result, data);
			}
			return result;
		}

		// 1) check in-memory cache
		Thumbnail result = cache.get(source);
		if (result != null) {
			CompletedFuture<Thumbnail> fr = new CompletedFuture<Thumbnail>(result, null);
			if (listener != null) {
				/* invoke listener inline as the future is completed */
				listener.onCompletion(fr, data);
			}
			return fr;
		}

		// 2) create a job to check file cache or create the thumbnail
		File destination = getNameInCache(source);
		GetThumbnailTask task = new GetThumbnailTask(source, destination, resizer, SWT.HIGH, display, this,
				new CallbackHolder(source, listener, data));
		Future<Thumbnail> future = executor.submit(task);
		tasks.put(source, future);
		fireStatusChanged();
		return future;
	}

	File getNameInCache(File source) {
		String name = source.getName();
		int id = name.lastIndexOf(".");
		if (id > 0) {
			// remove ".jpeg" or ".jpg"
			name = name.substring(0, id);
		}
		return new File(cacheDirectory + File.separator + name + THUMBNAIL_EXTENSION);
	}

	public boolean isUseExifThumbnail() {
		return useExifThumbnail;
	}

	@Override
	public void onCompletion(final Future<Thumbnail> result, final Object data) {
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				/*
				 * Run callback on UI thread to avoid synchronisation of
				 * internal data structures (and UI updates will move onto UI
				 * thread anyway).
				 */

				CallbackHolder holder = (CallbackHolder) data;

				// 1) update internal state
				tasks.remove(holder.source);
				fireStatusChanged();

				// 2) cache thumbnail
				try {
					Thumbnail thumb = result.get();
					// TODO only cache resized thumbnails?
					cache.add(holder.source, thumb);
				} catch (InterruptedException e) {
					/* can't happen, future is already done */
				} catch (CancellationException e) {
					/* ignore, client listener should handle logging */
				} catch (ExecutionException e) {
					/* ignore, client listener should handle logging */
				}

				// 3) invoke clients listener
				FutureCompletionListener<Thumbnail> delegateListener = holder.listener;
				if (delegateListener != null) {
					delegateListener.onCompletion(result, holder.data);
				}
			}
		});
	}

	public long purge() {
		SWTUtils.assertOnUIThread();
		long bytesDeleted = 0;
		for (File f : cacheDirectory.listFiles()) {
			if (f.getName().endsWith(THUMBNAIL_EXTENSION)) {
				bytesDeleted += f.length();
				if (!f.delete()) {
					LOG.debug("Failed to delete {} from file cache", f.getName());
				}
			}
		}
		cache.clear();
		return bytesDeleted;
	}

	@Override
	public void remove(File source) {
		SWTUtils.assertOnUIThread();

		// 1) cancel pending resize job
		Future<Thumbnail> future = tasks.remove(source);
		if (future != null && !future.isDone()) {
			future.cancel(true);
		}

		// 2) remove from cache
		cache.remove(source);

		// 3) delete file
		File cachedFile = getNameInCache(source);
		if (cachedFile.exists()) {
			if (!cachedFile.delete()) {
				LOG.info("Failed to delete thumbnail {}", cachedFile);
			}
		}
	}

	public void removeJobListener(JobListener listener) {
		listeners.remove(listener);
	}

	public void setUseExifThumbnail(boolean useExif) {
		useExifThumbnail = useExif;
		cache.clear();// AllFrom(source);
	}

	// private void put(Object name, ImageData data) {
	// ImageLoader saver = new ImageLoader();
	// saver.data = new ImageData[] { data };
	// File image = createFileName(extractSourceFileName(name));
	// saver.save(image.getAbsolutePath(), SWT.IMAGE_JPEG);
	// }

	private static class CallbackHolder {
		final Object data;
		final FutureCompletionListener<Thumbnail> listener;
		final File source;

		public CallbackHolder(File source, FutureCompletionListener<Thumbnail> callback, Object data) {
			this.source = source;
			this.listener = callback;
			this.data = data;
		}
	}

	private class GetThumbnailTask extends ListenableCancellableTask<Thumbnail> {
		private File destination;
		private Display display;
		private int interpolationLevel = SWT.HIGH;
		private ResizeStrategy resizer;
		private File source;
		private long timeTaken;

		public GetThumbnailTask(File source, File destination, ResizeStrategy resizer, int interpolationLevel,
				Display display, FutureCompletionListener<Thumbnail> listener, Object data) {
			super(listener, data);
			this.destination = destination;
			this.source = source;
			this.resizer = resizer;
			this.interpolationLevel = interpolationLevel;
			this.display = display;
		}

		@Override
		public final Thumbnail call() throws Exception {
			long start = System.currentTimeMillis();
			LOG.info("Executing Task {}", this);
			try {
				return doCall();
			} catch (Exception e) {
				/* log else we won't see the exception until get() */
				LOG.info("Task failed", e);
				throw e;
			} finally {
				timeTaken = System.currentTimeMillis() - start;
				LOG.info("Task finished in {}ms", timeTaken);
			}
		}

		protected ImageData createThumbnail(Metadata meta) {
			/**
			 * BUG: new Image(d,"..") calls into native JPEG loading routine. On
			 * OSX this routine already corrects the orientation of the photo.
			 */
			int rotate = OS.isMac() ? SWT.NONE : detectRotation(meta);

			Image thumbnail;
			LOG.debug("Loading image [{}]", source.getName());
			Image image = new Image(display, source.getAbsolutePath());
			Point current = new Point(image.getBounds().width, image.getBounds().height);
			LOG.debug("Loaded [{}] size = {}", source.getName(), current.x + "x" + current.y);

			// if the image will be resized, compute the size of the box as if
			// it were rotated
			Point thumbSize;
			if (rotate == SWT.RIGHT || rotate == SWT.LEFT) {
				thumbSize = swapXAndY(resizer.resize(swapXAndY(current)));
			} else {
				thumbSize = resizer.resize(current);
			}
			LOG.debug("Resizing image [{}] to {}", source.getName(), thumbSize.x + "x" + thumbSize.y);
			thumbnail = new Image(display, thumbSize.x, thumbSize.y);
			GC gc = new GC(thumbnail);
			gc.setAntialias(SWT.ON);
			gc.setInterpolation(interpolationLevel);
			gc.drawImage(image, 0, 0, current.x, current.y, 0, 0, thumbSize.x, thumbSize.y);
			image.dispose();
			ImageData data = rotate == SWT.NONE ? thumbnail.getImageData() : SWTUtils.rotate(thumbnail.getImageData(),
					rotate);
			thumbnail.dispose();
			gc.dispose();
			return data;
		}

		private int detectRotation(Metadata meta) {
			if (meta == null) {
				return SWT.NONE;
			}
			if (!meta.containsDirectory(ExifDirectory.class)) {
				return SWT.NONE;
			}
			ExifDirectory exif = (ExifDirectory) meta.getDirectory(ExifDirectory.class);
			if (!exif.containsTag(ExifDirectory.TAG_ORIENTATION)) {
				return SWT.NONE;
			}
			Object o = exif.getObject(ExifDirectory.TAG_ORIENTATION);
			if (o != null && o instanceof Integer) {
				/* from http://sylvana.net/jpegcrop/exif_orientation.html */
				switch ((Integer) o) {
				case 1: /* 0 */
					return SWT.NONE;
				case 3: /* 180 */
					LOG.debug("Rotating image [{}] by 180 degrees", source.getName());
					return SWT.DOWN;
				case 8: /* 90 */
					LOG.debug("Rotating image [{}] by 90 degrees", source.getName());
					return SWT.LEFT;
				case 6: /* 270 */
					LOG.debug("Rotating image [{}] by 270 degrees", source.getName());
					return SWT.RIGHT;
				}
			}

			return SWT.NONE;
		}

		public Thumbnail doCall() {
			Metadata meta = loadMeta();

			// 1) check for embedded thumbnail
			if (useExifThumbnail && meta != null) {
				ImageData exifThumb = getThumbFromMeta(meta);
				if (exifThumb != null) {
					try {
						int rotation = detectRotation(meta);
						return new Thumbnail(meta, resizeThumbnailFromExif(exifThumb, meta, rotation));
					} catch (Exception e) {
						LOG.warn("Failed to create thumnail from embedded thumbnail", e);
					}
				}
			}

			// 2) check file cache, if file can be loaded we are done
			if (destination.exists() && destination.lastModified() >= source.lastModified()) {
				// load from file
				LOG.debug("Thumbnail exists in file cache [{}]", destination.getName());
				try {
					// TODO cache meta in cache directory?
					// TODO check dimensions of file are suitable
					ImageData data = new ImageData(destination.getAbsolutePath());
					return new Thumbnail(loadMeta(), data);
				} catch (SWTException e) {
					// failed to load, continue to (re)create the thumbnail
					LOG.info("Failed to load thumbnail [" + destination.getName() + "]", e);
				}
			}

			// 3) resize image to create thumbnail
			ImageData data = createThumbnail(meta);
			Thumbnail result = new Thumbnail(meta, data);

			// 4) finally add a new task to write the thumbnail to disk
			//
			// A race is created. If the "callback" does not run
			// before another request is made to load the same thumbnail we'll
			// recreate it all over (as it won't be in the cache).
			try {
				executor.submit(new SaveThumbnailTask(data, destination));
			} catch (RejectedExecutionException e) {
				/* thrown if thumbnails still loading during shutdown, ignore */
			}
			return result;
		}

		private ImageData getThumbFromMeta(Metadata meta) {
			if (!meta.containsDirectory(ExifDirectory.class)) {
				return null;
			}
			ExifDirectory exif = (ExifDirectory) meta.getDirectory(ExifDirectory.class);
			try {
				ImageData data = new ImageData(new ByteArrayInputStream(exif.getThumbnailData()));
				LOG.debug("Found EXIF thumbnail with dimensions {}x{} in '{}'", new Object[] { data.width, data.height,
						source.getName() });
				return data;
			} catch (SWTException e) {
				LOG.warn("Failed to create image from EXIF thumbnail from '" + source.getName() + "'", e);
			} catch (MetadataException e) {
				LOG.warn("Failed to extract EXIF thumbnail from '" + source.getName() + "'", e);
			}
			return null;
		}

		private Metadata loadMeta() {
			try {
				return JpegMetadataReader.readMetadata(source);
			} catch (JpegProcessingException e) {
				LOG.warn("Failed to load meta-data for [" + source.getName() + "]", e);
			}
			return null;
		}

		private ImageData resizeThumbnailFromExif(ImageData exifThumbData, Metadata meta, int rotation)
				throws MetadataException {
			// resize & rotate using actual photo dimensions
			JpegDirectory jpegDir = (JpegDirectory) meta.getDirectory(JpegDirectory.class);
			Point imageSize = new Point(jpegDir.getImageWidth(), jpegDir.getImageHeight());
			if (rotation != SWT.NONE) {
				exifThumbData = SWTUtils.rotate(exifThumbData, rotation);
				if (rotation == SWT.LEFT || rotation == SWT.RIGHT) {
					imageSize = swapXAndY(imageSize);
				}
			}

			/*
			 * LX3 produces thumbnails with black bars when the aspect ratio not
			 * 3:2, correct by copying only a portion of thumbnail to target
			 */

			Point idealThumbSize = resizer.resize(imageSize);
			Point currentThumbSize = new Point(exifThumbData.width, exifThumbData.height);
			float sourceAspect = (float) imageSize.x / imageSize.y;

			// if the image is portrait then we need to scale y to fit
			int marginX;
			int marginY;
			if (rotation == SWT.LEFT || rotation == SWT.RIGHT) {
				float expectedThumbWidth = currentThumbSize.y * sourceAspect;
				marginX = Math.round((currentThumbSize.x - expectedThumbWidth) / 2);
				marginY = 0;
			} else {
				float expectedThumbHeight = currentThumbSize.x / sourceAspect;
				marginX = 0;
				marginY = Math.round((currentThumbSize.y - expectedThumbHeight) / 2); // centre
			}

			GC gc = null;
			Image newThumb = null, exifThumb = null;
			try {
				newThumb = new Image(display, idealThumbSize.x, idealThumbSize.y);
				exifThumb = new Image(display, exifThumbData);
				gc = new GC(newThumb);
				gc.setInterpolation(SWT.HIGH);
				gc.drawImage(exifThumb, marginX, marginY, exifThumbData.width - (2 * marginX), exifThumbData.height
						- (2 * marginY), 0, 0, idealThumbSize.x, idealThumbSize.y);
				return newThumb.getImageData();
			} finally {
				if (exifThumb != null)
					exifThumb.dispose();
				if (newThumb != null)
					newThumb.dispose();
				if (gc != null)
					gc.dispose();
			}
		}

		private Point swapXAndY(Point p) {
			return new Point(p.y, p.x);
		}

		@Override
		public String toString() {
			return String.format("Loading or create thumbnail for [%s]", source.getAbsolutePath());
		}
	}

	/**
	 * Saves the thumbnail data to disk, this is done as a separate task so the
	 * UI gets the thumbnail as soon as possible and the less important task of
	 * saving to disk is deferred.
	 */
	private static class SaveThumbnailTask implements Callable<File> {

		ImageData data;
		File destination;

		public SaveThumbnailTask(ImageData data, File destination) {
			this.data = data;
			this.destination = destination;
		}

		@Override
		public File call() throws Exception {
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(destination);
				ImageLoader loader = new ImageLoader();
				loader.data = new ImageData[] { data };
				LOG.debug("Writing thumbnail to [{}]", destination.getAbsolutePath());
				loader.save(fos, SWT.IMAGE_PNG);
			} finally {
				try {
					if (fos != null) {
						fos.close();
					}
				} catch (IOException e) {
				}
			}
			return destination;
		}

		@Override
		public String toString() {
			return String.format("Save thumbnail to [%s]", destination.getName());
		}
	}
}