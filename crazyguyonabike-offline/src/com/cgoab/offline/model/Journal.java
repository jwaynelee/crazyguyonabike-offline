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

// TOP level journal node
public class Journal {

	private static final Pattern DAY_PATTERN = Pattern.compile("^[dD]ay (\\d+)");
	public static final int UNSET_DOC_ID = -1;
	public static final long NEVER_SAVED_TIMESTAMP = -1;

	private List<JournalListener> listeners = new ArrayList<JournalListener>();
	private String name;
	private List<Page> pages = new ArrayList<Page>();
	private File file;
	private Map<String, Page> loadedPhotos = new HashMap<String, Page>();
	private boolean isDirty;
	private int nextLocalPageId = 0;
	private long lastModifiedTimestamp = NEVER_SAVED_TIMESTAMP;
	private int docIdHint = UNSET_DOC_ID;
	private Boolean resizeImagesBeforeUpload = null; // null = unset

	public Journal(File file, String name) {
		this.name = name;
		this.file = file;
	}

	public void setResizeImagesBeforeUpload(Boolean resizeImagesBeforeUpload) {
		this.resizeImagesBeforeUpload = resizeImagesBeforeUpload;
	}

	/**
	 * Returns if images should be resized before upload, <tt>null</tt> if
	 * unset.
	 * 
	 * @return
	 */
	public Boolean isResizeImagesBeforeUpload() {
		return resizeImagesBeforeUpload;
	}

	public void setDocIdHint(int docIdHint) {
		this.docIdHint = docIdHint;
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

	public String getName() {
		return name;
	}

	/**
	 * Creates a new page at the end of this journal, the page inherits settings
	 * from the previous page and has its date set to the following day.
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
						title = "Day " + (Integer.parseInt(m.group(1)) + 1);
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

	public List<Page> getPages() {
		return Collections.unmodifiableList(pages);
	}

	@Override
	public String toString() {
		return name + " (" + pages.size() + " pages)";
	}

	private void firePhotosAdded(List<Photo> photos, Page page) {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.photosAdded(page.getPhotos(), page);
		}
	}

	private void fireJournalDirtyChange() {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.journalDirtyChange();
		}
	}

	private void firePageRemoved(Page page) {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.pageDeleted(page);
		}
	}

	private void firePageAdded(Page page) {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.pageAdded(page);
		}
	}

	private void firePhotosRemoved(List<Photo> photos, Page page) {
		for (JournalListener listener : new ArrayList<JournalListener>(listeners)) {
			listener.photosRemoved(photos, page);
		}
	}

	public void removePage(Page page) {
		Assert.isTrue(page.getJournal() == this);
		isDirty = true;
		pages.remove(page);
		firePhotosRemoved(page.getPhotos(), page);
		firePageRemoved(page);
	}

	public void setDirty(boolean state) {
		isDirty = state;
		fireJournalDirtyChange();
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isDirty() {
		return isDirty;
	}

	public void addPage(Page newPage) {
		Assert.isTrue(newPage.getJournal() == this);
		Assert.isTrue(newPage.getLocalId() != Page.UNSET_LOCAL_ID);
		if (nextLocalPageId <= newPage.getLocalId()) {
			nextLocalPageId = newPage.getLocalId() + 1;
		}
		for (Page p : pages) {
			if (newPage.getLocalId() == p.getLocalId()) {
				throw new AssertionError("Duplicate local page id");
			}
		}
		if (!pages.contains(newPage)) {
			pages.add(newPage);
		}
		firePageAdded(newPage);
		setDirty(true);
	}

	// private String getImageName(Photo photo) {
	// String name = photo.getImageName();
	// if (!StringUtils.isEmpty(name)) {
	// return name;
	// }
	// return photo.getFile().getName();
	// }

	void photosRemoved(List<Photo> photos, Page page) {
		for (Photo photo : photos) {
			loadedPhotos.remove(photo.getFile().getName());
		}
		firePhotosRemoved(photos, page);
	}

	void photosAdded(List<Photo> photos, Page page) {
		for (Photo photo : photos) {
			loadedPhotos.put(photo.getFile().getName(), page);
		}
		firePhotosAdded(photos, page);
	}

	void checkForDuplicatePhotoInJournal(Photo photo) {
		Page page = loadedPhotos.get(photo.getFile().getName());
		if (page != null) {
			throw new IllegalStateException();
		}
	}

	public void setLastModifiedWhenLoaded(long lastModifiedTimestamp) {
		this.lastModifiedTimestamp = lastModifiedTimestamp;
	}

	public long getLastModifiedWhenLoaded() {
		return lastModifiedTimestamp;
	}

	public void addListener(JournalListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void removeListener(JournalListener listener) {
		listeners.remove(listener);
	}

	public Map<String, Page> getPhotoMap() {
		return loadedPhotos;
	}
}