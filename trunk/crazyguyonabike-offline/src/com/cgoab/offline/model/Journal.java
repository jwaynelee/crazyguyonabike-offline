package com.cgoab.offline.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cgoab.offline.util.Assert;

/**
 * A Journal holds the pages to to be uploaded to the server.
 * 
 * The name of this journal has no correlation to the journal (document) to
 * which pages are added too. The actual document is resolved at upload time.
 * Once an upload occurs id of the document (doc_id) to which the pages were
 * added is available from {@link #getDocIdHint()}.
 */
public class Journal {

	private static final Pattern DAY_PATTERN = Pattern.compile("^[dD]ay (\\d+)");
	public static final String HIDE_UPLOADED_CONTENT = "hideUploadedContent";
	public static final long NEVER_SAVED_TIMESTAMP = -1;
	public static final String RESIZE_IMAGES_BEFORE_UPLOAD = "resizeImagesBeforeUpload";
	public static final int UNSET_DOC_ID = -1;
	public static final String USE_EXIF_THUMBNAIL = "useExifThumbnail";
	private Map<String, Object> data = new HashMap<String, Object>();
	private int docIdHint = UNSET_DOC_ID;
	private File file;
	private boolean hideUploadedContent = true;
	private boolean isDirty;
	private long lastModifiedTimestamp = NEVER_SAVED_TIMESTAMP;
	private List<JournalListener> listeners = new ArrayList<JournalListener>();
	private Map<String, Page> loadedPhotos = new HashMap<String, Page>();
	private String name;

	private List<Page> pages = new ArrayList<Page>();

	private Boolean resizeImagesBeforeUpload = null; // null = unset

	private PropertyChangeSupport support = new PropertyChangeSupport(this);

	private Boolean useExifThumbnail;

	public Journal(File file, String name) {
		this.name = name;
		this.file = file;
	}

	public void addJournalListener(JournalListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	void addPage(Page newPage) {
		Assert.isTrue(newPage.getJournal() == this);
		if (pages.contains(newPage)) {
			throw new IllegalArgumentException("Page is already added to journal!");
		}
		pages.add(newPage);
		firePageAdded(newPage);
		setDirty(true);
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		support.addPropertyChangeListener(listener);
	}

	/**
	 * Creates a new page at the end of this journal, the new page will inherit
	 * settings from the previous page, have its date set to the following day
	 * and it's title set the the next day in sequence (if it matches "day \d").
	 * 
	 * @return
	 */
	public Page createNewPage() {
		Page page = new Page(this);
		String title = "NEW PAGE";
		if (pages.size() > 0) {
			Page previousPage = pages.get(pages.size() - 1);
			page.setDate(previousPage.getDate().plusDays(1));
			page.setBold(previousPage.isBold());
			page.setItalic(previousPage.isItalic());
			page.setIndent(previousPage.getIndent());
			page.setFormat(previousPage.getFormat());
			page.setHeadingStyle(previousPage.getHeadingStyle());
			page.setVisible(previousPage.isVisible());
			String previousTitle = previousPage.getTitle();
			if (previousTitle != null) {
				Matcher m = DAY_PATTERN.matcher(previousTitle);
				if (m.matches()) {
					try {
						title = previousTitle.charAt(0) + "ay " + (Integer.parseInt(m.group(1)) + 1);
					} catch (NumberFormatException e) {
						// ignore
					}
				}
			}
		}
		page.setTitle(title);
		addPage(page);
		return page;
	}

	private void fireJournalDirtyChange() {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.journalDirtyChange();
		}
	}

	private void firePageAdded(Page page) {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.pageAdded(page);
		}
	}

	private void firePageRemoved(Page page) {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.pageDeleted(page);
		}
	}

	private void firePhotosAdded(List<Photo> photos, Page page) {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.photosAdded(page.getPhotos(), page);
		}
	}

	private void firePhotosRemoved(List<Photo> photos, Page page) {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.photosRemoved(photos, page);
		}
	}

	/**
	 * Associates some transient data with this journal instance.
	 * 
	 * @param key
	 * @return
	 */
	public Object getData(String key) {
		return data.get(key);
	}

	public int getDocIdHint() {
		return docIdHint;
	}

	/**
	 * Returns the file to which this journal is persisted too.
	 * 
	 * @return
	 */
	public File getFile() {
		return file;
	}

	/**
	 * Provides the timestamp ({@link File#lastModified()}) of the file that
	 * this journal was loaded from, used to detect if the underlying file was
	 * modified since the journal was loaded.
	 * 
	 * @return last modified timestamp or {@value #NEVER_SAVED_TIMESTAMP} if the
	 *         journal is new.
	 */
	public long getLastModifiedWhenLoaded() {
		return lastModifiedTimestamp;
	}

	public String getName() {
		return name;
	}

	public List<Page> getPages() {
		return Collections.unmodifiableList(pages);
	}

	public Map<String, Page> getPhotoMap() {
		return loadedPhotos;
	}

	public boolean isDirty() {
		return isDirty;
	}

	public boolean isHideUploadedContent() {
		return hideUploadedContent;
	}

	/**
	 * Returns if images should be resized before upload, <tt>null</tt> if not
	 * yet set.
	 * 
	 * @return
	 */
	public Boolean isResizeImagesBeforeUpload() {
		return resizeImagesBeforeUpload;
	}

	public Boolean isUseExifThumbnail() {
		return useExifThumbnail;
	}

	// private String getImageName(Photo photo) {
	// String name = photo.getImageName();
	// if (!StringUtils.isEmpty(name)) {
	// return name;
	// }
	// return photo.getFile().getName();
	// }

	void photosAdded(List<Photo> photos, Page page) {
		if (photos.size() > 0) {
			for (Photo photo : photos) {
				Page duplicate = loadedPhotos.put(photo.getFile().getName(), page);
				/* should already be checked, check again for sanity */
				Assert.isNull(duplicate, "Photo [" + photo + "] already exists on page " + duplicate);
			}
			firePhotosAdded(photos, page);
		}
	}

	void photosRemoved(List<Photo> photos, Page page) {
		for (Photo photo : photos) {
			loadedPhotos.remove(photo.getFile().getName());
		}
		firePhotosRemoved(photos, page);
	}

	// void checkForDuplicatePhotoInJournal(Photo photo) {
	// Page page = loadedPhotos.get(photo.getFile().getName());
	// if (page != null) {
	// throw new IllegalStateException();
	// }
	// }

	public void removeListener(JournalListener listener) {
		listeners.remove(listener);
	}

	public void removePage(Page page) {
		Assert.isTrue(page.getJournal() == this);
		pages.remove(page);
		photosRemoved(page.getPhotos(), page);
		firePageRemoved(page);
		setDirty(true);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		support.removePropertyChangeListener(listener);
	}

	/**
	 * Retrieves some transient data associated with this journal, may not be
	 * null.
	 * 
	 * @param key
	 * @param value
	 */
	public void setData(String key, Object value) {
		Assert.notNull(key, "key must not be null");
		Assert.notNull(value, "value must not be null");
		data.put(key, value);
	}

	public void setDirty(boolean state) {
		isDirty = state;
		fireJournalDirtyChange();
	}

	public void setDocIdHint(int docIdHint) {
		this.docIdHint = docIdHint;
		setDirty(true);
	}

	public void setHideUploadedContent(boolean hideUploadedContent) {
		support.firePropertyChange(HIDE_UPLOADED_CONTENT, this.hideUploadedContent,
				this.hideUploadedContent = hideUploadedContent);
		setDirty(true);
	}

	public void setLastModifiedWhenLoaded(long lastModifiedTimestamp) {
		this.lastModifiedTimestamp = lastModifiedTimestamp;
	}

	public void setName(String name) {
		this.name = name;
		setDirty(true);
	}

	/**
	 * Configures if this journal should resize images before they are sent to
	 * the server, default is <tt>null</tt>.
	 * 
	 * @param resizeImagesBeforeUpload
	 */
	public void setResizeImagesBeforeUpload(boolean resizeImagesBeforeUpload) {
		support.firePropertyChange(RESIZE_IMAGES_BEFORE_UPLOAD, this.resizeImagesBeforeUpload,
				this.resizeImagesBeforeUpload = resizeImagesBeforeUpload);
		setDirty(true);
	}

	public void setUseExifThumbnail(boolean useExifThumbnail) {
		support.firePropertyChange(USE_EXIF_THUMBNAIL, this.useExifThumbnail, this.useExifThumbnail = useExifThumbnail);
		setDirty(true);
	}

	@Override
	public String toString() {
		return name + " (" + pages.size() + " pages)";
	}
}