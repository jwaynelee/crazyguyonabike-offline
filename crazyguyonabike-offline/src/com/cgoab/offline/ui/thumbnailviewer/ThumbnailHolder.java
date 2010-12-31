package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.concurrent.Future;

import org.eclipse.swt.graphics.Image;

import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;
import com.drew.metadata.Metadata;

/**
 * Holder for thumbnail image data and associated detail such as filename and
 * cached meta-data.
 * 
 * The ImageData may be loaded on a background thread, thus at any given time
 * the image may be null, {@link #hasImage()} can be used to test for this case.
 */
public class ThumbnailHolder {

	private Image image;

	private boolean disposed = false;

	private final int width = ThumbnailViewer.THUMBNAIL_WIDTH + ThumbnailViewer.PADDING_INSIDE
			+ ThumbnailViewer.PADDING_INSIDE;

	private final int height = ThumbnailViewer.THUMBNAIL_HEIGHT + ThumbnailViewer.PADDING_INSIDE
			+ ThumbnailViewer.TEXT_HEIGHT;

	// absolute x location of this widget
	private int x;

	private Metadata meta;

	private File file;

	private String txt;

	private Object data;

	private int opacity;

	private Image overlay;

	private boolean failedToLoad;

	private Future<Thumbnail> future;

	public ThumbnailHolder(File file, String txt) {
		this.file = file;
		this.txt = txt;
	}

	public void setOpacity(int opacity) {
		this.opacity = opacity;
	}

	public int getOpacity() {
		return opacity;
	}

	public void setOverlay(Image overlay) {
		this.overlay = overlay;
	}

	public Image getOverlay() {
		return overlay;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public Object getData() {
		return data;
	}

	public String getText() {
		throwIfDisposed();
		return txt;
	}

	public int getHeight() {
		throwIfDisposed();
		return height;
	}

	public int getWidth() {
		throwIfDisposed();
		return width;
	}

	public Image getImage() {
		throwIfDisposed();
		return image;
	}

	public void setImage(Image image) {
		throwIfDisposed();
		this.image = image;
	}

	public void setMeta(Metadata meta) {
		throwIfDisposed();
		this.meta = meta;
	}

	public File getFile() {
		throwIfDisposed();
		return file;
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

	public int getX() {
		throwIfDisposed();
		return x;
	}

	public Metadata getMeta() {
		throwIfDisposed();
		return meta;
	}

	// returns true if the given x co-ordinate is over this image
	public boolean inside(int x, int y) {
		throwIfDisposed();
		if (x > this.x && x < (this.x + width)) {
			return y > ThumbnailViewer.PADDING_TOP && y < (ThumbnailViewer.PADDING_TOP + height);
		}
		return false;
	}

	public synchronized boolean isDisposed() {
		return disposed;
	}

	public void dispose() {
		if (image != null) {
			image.dispose();
		}
		if (overlay != null) {
			// overlay.dispose();
		}
		image = null;
		disposed = true;
	}

	private void throwIfDisposed() {
		if (disposed) {
			throw new IllegalStateException("Already disposed");
		}
	}

	public void setFailedToLoad(boolean failedToLoad) {
		this.failedToLoad = failedToLoad;
	}

	public boolean isFailedToLoad() {
		return failedToLoad;
	}

	public void setFuture(Future<Thumbnail> future) {
		if (this.future != null) {
			throw new IllegalStateException();
		}
		this.future = future;
	}

	public Future<Thumbnail> getFuture() {
		return future;
	}
}
