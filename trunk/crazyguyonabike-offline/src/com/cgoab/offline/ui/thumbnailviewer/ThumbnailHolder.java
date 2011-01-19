package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.concurrent.Future;

import org.eclipse.swt.graphics.Image;

import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;

/**
 * Holder for thumbnail image data and associated detail such as filename and
 * cached meta-data.
 * 
 * The ImageData may be loaded on a background thread, thus at any given time
 * the image may be null, {@link #hasImage()} can be used to test for this case.
 */
public class ThumbnailHolder {

	private Object data;

	private boolean disposed = false;

	private boolean failedToLoad;

	private final File file;

	private Future<Thumbnail> future;

	private final int height = ThumbnailViewer.THUMBNAIL_HEIGHT + ThumbnailViewer.PADDING_INSIDE
			+ ThumbnailViewer.TEXT_HEIGHT;

	private Image image;

	private int opacity;

	private Image overlay;

	private String text;

	private final int width = ThumbnailViewer.THUMBNAIL_WIDTH + ThumbnailViewer.PADDING_INSIDE
			+ ThumbnailViewer.PADDING_INSIDE;

	// absolute x location of this widget
	private int x;

	public ThumbnailHolder(File file, String txt) {
		this.file = file;
		this.text = txt;
	}

	public void dispose() {
		if (!disposed) {
			disposed = true;
			if (image != null) {
				image.dispose();
			}
			if (future != null && !future.isDone()) {
				future.cancel(true);
			}
		}
	}

	public Object getData() {
		throwIfDisposed();
		return data;
	}

	public File getFile() {
		throwIfDisposed();
		return file;
	}

	public Future<Thumbnail> getFuture() {
		throwIfDisposed();
		return future;
	}

	public int getHeight() {
		throwIfDisposed();
		return height;
	}

	public Image getImage() {
		throwIfDisposed();
		return image;
	}

	public int getOpacity() {
		throwIfDisposed();
		return opacity;
	}

	public Image getOverlay() {
		throwIfDisposed();
		return overlay;
	}

	public String getText() {
		throwIfDisposed();
		return text;
	}

	public int getWidth() {
		throwIfDisposed();
		return width;
	}

	public int getX() {
		throwIfDisposed();
		return x;
	}

	// returns true if the given x co-ordinate is over this image
	public boolean inside(int x, int y) {
		throwIfDisposed();
		if (x > this.x && x < (this.x + width)) {
			return y > ThumbnailViewer.PADDING_TOP && y < (ThumbnailViewer.PADDING_TOP + height);
		}
		return false;
	}

	public boolean isDisposed() {
		return disposed;
	}

	public boolean isFailedToLoad() {
		throwIfDisposed();
		return failedToLoad;
	}

	public void setData(Object data) {
		throwIfDisposed();
		this.data = data;
	}

	public void setFailedToLoad(boolean failedToLoad) {
		throwIfDisposed();
		this.failedToLoad = failedToLoad;
	}

	public void setFuture(Future<Thumbnail> future) {
		throwIfDisposed();
		if (this.future != null) {
			throw new IllegalStateException();
		}
		this.future = future;
	}

	public void setImage(Image image) {
		throwIfDisposed();
		this.image = image;
	}

	public void setOpacity(int opacity) {
		throwIfDisposed();
		this.opacity = opacity;
	}

	public void setOverlay(Image overlay) {
		throwIfDisposed();
		this.overlay = overlay;
	}

	/**
	 * Distance this thumbnail appears from the start of the list of thumbnails,
	 * starting from 0
	 * 
	 * @param x
	 */
	public void setX(int x) {
		throwIfDisposed();
		this.x = x;
	}

	private void throwIfDisposed() {
		if (disposed) {
			throw new IllegalStateException("Already disposed");
		}
	}

	@Override
	public String toString() {
		return file.getName();
	}
}
