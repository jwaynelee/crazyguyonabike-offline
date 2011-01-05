package com.cgoab.offline.model;

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
	public static final long NEVER_SAVED_TIMESTAMP = -1;
	public static final int UNSET_DOC_ID = -1;

	private int docIdHint = UNSET_DOC_ID;
	private File file;
	private boolean isDirty;
	private long lastModifiedTimestamp = NEVER_SAVED_TIMESTAMP;
	private List<JournalListener> listeners = new ArrayList<JournalListener>();
	private Map<String, Page> loadedPhotos = new HashMap<String, Page>();
	private String name;
	private int nextLocalPageId = 0;
	private List<Page> pages = new ArrayList<Page>();
	private Boolean resizeImagesBeforeUpload = null; // null = unset
	private Boolean useExifThumbnail = null; // null = unset

	public Journal(File file, String name) {
		this.name = name;
		this.file = file;
	}

	public void addListener(JournalListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void addPage(Page newPage) {
		Assert.isTrue(newPage.getJournal() == this);
		Assert.isTrue(newPage.getLocalId() != Page.UNSET_LOCAL_ID);
		if (pages.contains(newPage)) {
			throw new IllegalArgumentException("Page is already added to journal!");
		}
		if (nextLocalPageId <= newPage.getLocalId()) {
			nextLocalPageId = newPage.getLocalId() + 1;
		}
		for (Page p : pages) {
			if (newPage.getLocalId() == p.getLocalId()) {
				throw new AssertionError("Duplicate local page id");
			}
		}
		pages.add(newPage);
		firePageAdded(newPage);
		setDirty(true);
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
		page.setLocalId(nextLocalPageId++);
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

	void photosAdded(List<Photo> photos, Page page) {
		for (Photo photo : photos) {
			Page duplicate = loadedPhotos.put(photo.getFile().getName(), page);
			// should already be checked in Page, check for sanity
			Assert.isNull(duplicate, "Photo " + photo + " already exists on page " + duplicate);
		}
		firePhotosAdded(photos, page);
	}

	void photosRemoved(List<Photo> photos, Page page) {
		for (Photo photo : photos) {
			loadedPhotos.remove(photo.getFile().getName());
		}
		firePhotosRemoved(photos, page);
	}

	// private String getImageName(Photo photo) {
	// String name = photo.getImageName();
	// if (!StringUtils.isEmpty(name)) {
	// return name;
	// }
	// return photo.getFile().getName();
	// }

	public void removeListener(JournalListener listener) {
		listeners.remove(listener);
	}

	public void removePage(Page page) {
		Assert.isTrue(page.getJournal() == this);
		pages.remove(page);
		firePhotosRemoved(page.getPhotos(), page);
		firePageRemoved(page);
		setDirty(true);
	}

	// void checkForDuplicatePhotoInJournal(Photo photo) {
	// Page page = loadedPhotos.get(photo.getFile().getName());
	// if (page != null) {
	// throw new IllegalStateException();
	// }
	// }

	public void setDirty(boolean state) {
		isDirty = state;
		fireJournalDirtyChange();
	}

	public void setDocIdHint(int docIdHint) {
		this.docIdHint = docIdHint;
		setDirty(true);
	}

	public void setLastModifiedWhenLoaded(long lastModifiedTimestamp) {
		this.lastModifiedTimestamp = lastModifiedTimestamp;
		setDirty(true);
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
		this.resizeImagesBeforeUpload = resizeImagesBeforeUpload;
		setDirty(true);
	}

	public void setUseExifThumbnail(boolean useExifThumbnail) {
		this.useExifThumbnail = useExifThumbnail;
		setDirty(true);
	}

	@Override
	public String toString() {
		return name + " (" + pages.size() + " pages)";
	}
}