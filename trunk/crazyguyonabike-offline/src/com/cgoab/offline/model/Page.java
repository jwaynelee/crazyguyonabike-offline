package com.cgoab.offline.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.joda.time.LocalDate;

import com.cgoab.offline.util.Assert;

/**
 * A page in a journal.
 */
public class Page implements Cloneable {

	public static enum EditFormat {
		AUTO, LIST, MANUAL
	}

	public static enum HeadingStyle {
		LARGE, MEDIUM, SMALL
	}

	public static enum PhotosOrder {
		DATE, MANUAL, NAME;
	}

	public static final int INDENT_MAX = 10;

	public static final int INDENT_MIN = 1;

	public static final int UNSET_LOCAL_ID = -1;

	public static final int UNSET_SERVER_ID = -1;

	private boolean bold = false;

	private LocalDate date = new LocalDate(); // default to today

	private float distance;

	private EditFormat editFormat = EditFormat.AUTO;

	private HeadingStyle headingStyle = HeadingStyle.SMALL;

	private String headline;

	private int indent = 1;

	private boolean italic = false;

	private Journal journal;

	private int localId = UNSET_LOCAL_ID;

	private PhotosOrder order = PhotosOrder.MANUAL;

	private final List<Photo> photos = new ArrayList<Photo>();

	private int serverId = UNSET_SERVER_ID;

	private IDocument textDocument;

	private String title;

	private UploadState uploadState = UploadState.NEW;

	private boolean visible = true;

	public Page() {
	}

	public Page(Journal journal) {
		this.journal = journal;
	}

	@Override
	public Object clone() {
		try {
			// perform deep copy of photos
			Page copy = (Page) super.clone();
			for (Photo p : photos) {
				copy.photos.add(p.clone());
			}
			return copy;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public LocalDate getDate() {
		return date;
	}

	public float getDistance() {
		return distance;
	}

	public String getEditComment() {
		return null;
	}

	public EditFormat getFormat() {
		return editFormat;
	}

	public HeadingStyle getHeadingStyle() {
		return headingStyle;
	}

	public String getHeadline() {
		return headline;
	}

	public int getIndent() {
		return indent;
	}

	public Journal getJournal() {
		return journal;
	}

	public int getLocalId() {
		return localId;
	}

	/**
	 * Returns an unmodifiable copy of the photos attached to this page ordered
	 * as specified by {@link #getPhotosOrder()}.
	 * 
	 * @return
	 */
	public List<Photo> getPhotos() {
		// order before returning
		Comparator<Photo> comparator = getComparator(order);
		if (comparator != null) {
			Collections.sort(photos, comparator);
		}
		return Collections.unmodifiableList(new ArrayList<Photo>(photos));
	}

	public PhotosOrder getPhotosOrder() {
		return order;
	}

	/**
	 * Returns the ID this page was given when it was uploaded to the server or
	 * {@value #UNSET_SERVER_ID} if this page is not yet uploaded.
	 * 
	 * @return
	 */
	public int getServerId() {
		return serverId;
	}

	public UploadState getState() {
		return uploadState;
	}

	public String getText() {
		return textDocument == null ? null : textDocument.get();
	}

	public String getTitle() {
		return title;
	}

	private void assertIsEditable() {
		if (getState() == UploadState.UPLOADED) {
			throw new PageNotEditableException();
		}
	}

	/**
	 * Adds one or more photos to this page at a given location in the photos
	 * list.
	 * 
	 * @param toAdd
	 *            photos to be added
	 * @param insertionPoint
	 *            insertion point in photos list, <tt>-1</tt> can be used to
	 *            append to the end.
	 */
	public void addPhotos(List<Photo> toAdd, int insertionPoint) {
		assertIsEditable();
		assertValidInsertionPoint(insertionPoint);

		// check for duplicate file names...
		Map<Photo, Page> duplicates = new LinkedHashMap<Photo, Page>();
		Map<String, Page> journalPhotos = journal.getPhotoMap();

		for (Photo adding : toAdd) {
			String imageName = adding.getFile().getName();
			Page pageDuplicateFound = journalPhotos.get(imageName);
			// compare file name as this is what becomes the
			// image name on the server
			if (pageDuplicateFound != null) {
				duplicates.put(adding, pageDuplicateFound);
				continue;
			}
		}

		if (duplicates.size() > 0) {
			throw new DuplicatePhotoException(duplicates);
		}

		if (insertionPoint == -1) {
			photos.addAll(toAdd);
		} else {
			photos.addAll(insertionPoint, toAdd);
		}

		journal.setDirty(true);
		journal.photosAdded(toAdd, this);
	}

	public boolean isBold() {
		return bold;
	}

	public boolean isItalic() {
		return italic;
	}

	public boolean isVisible() {
		return visible;
	}

	/**
	 * Returns <tt>true</tt> if the insertion point is after the last uploaded
	 * photo.
	 * 
	 * @param insertionPoint
	 * @return
	 */
	private void assertValidInsertionPoint(int insertionPoint) {
		// append
		if (insertionPoint == -1 || insertionPoint == photos.size()) {
			return;
		}
		// check insertion point is after the last uploaded photo
		for (int i = photos.size() - 1; i > -1; --i) {
			Photo p = photos.get(i);
			if (p.getState() == UploadState.UPLOADED) {
				if (insertionPoint <= i) {
					throw new InvalidInsertionPointException();
				}
				return;
			}
		}
		return;
	}

	private void assertPageOwnsPhotos(List<Photo> photosToCheck) {
		for (Photo photo : photosToCheck) {
			if (!photos.contains(photo)) {
				throw new IllegalArgumentException("Page does not own photo " + photo);
			}
		}
	}

	public void movePhotos(List<Photo> toMove, int insertionPoint) {
		assertIsEditable();
		assertValidInsertionPoint(insertionPoint);
		assertPageOwnsPhotos(toMove);

		if (uploadState == UploadState.PARTIALLY_UPLOAD) {
			// 1) can't move uploaded/error photo
			for (Object o : toMove) {
				Photo p = (Photo) o;
				if (p.getState() != UploadState.NEW && p.getState() != UploadState.ERROR) {
					return;// TODO error box?
				}
			}
		}

		journal.setDirty(true);

		// walk up to the insertion point, counting how many selected
		// items we find
		int found = 0;
		for (int i = 0; i < insertionPoint; ++i) {
			if (toMove.contains(photos.get(i))) {
				found++;
			}
		}

		// remove the selection
		photos.removeAll(toMove);

		// re-insert the selection at adjusted index
		photos.addAll(insertionPoint - found, toMove);
		setPhotosOrder(PhotosOrder.MANUAL);
	}

	public void removePhotos(List<Photo> toRemove) {
		assertIsEditable();
		assertPageOwnsPhotos(toRemove);

		// only remove NEW or ERROR photos
		for (Photo p : toRemove) {
			if (p.getState() != UploadState.NEW && p.getState() != UploadState.ERROR) {
				return; // TODO throw?
			}
		}

		photos.removeAll(toRemove);
		journal.setDirty(true);
		journal.photosRemoved(toRemove, this);
	}

	public void setBold(boolean bold) {
		this.bold = bold;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public void setDistance(float distance) {
		this.distance = distance;
	}

	public void setFormat(EditFormat editFormat) {
		this.editFormat = editFormat;
	}

	public void setHeadingStyle(HeadingStyle headingStyle) {
		Assert.notNull(headingStyle);
		this.headingStyle = headingStyle;
	}

	public void setHeadline(String headline) {
		this.headline = headline;
	}

	public void setIndent(int indent) {
		if (indent > INDENT_MAX) {
			throw new IndexOutOfBoundsException("indent must be <= " + INDENT_MAX);
		}
		if (indent < INDENT_MIN) {
			throw new IndexOutOfBoundsException("indent must be >= " + INDENT_MIN);
		}
		this.indent = indent;
	}

	public void setItalic(boolean italic) {
		this.italic = italic;
	}

	public void setLocalId(int id) {
		localId = id;
	}

	private static Comparator<Photo> getComparator(PhotosOrder order) {
		switch (order) {
		case NAME:
			return FileNameComparator.INSTANCE;
		case DATE:
			return FileDateComparator.INSTANCE;
		}
		return null;
	}

	void setPhotos(List<Photo> p) {
		photos.addAll(p);
		journal.photosAdded(p, this);
	}

	public void setPhotosOrder(PhotosOrder order) {
		this.order = order;
	}

	public void setServerId(int id) {
		this.serverId = id;
	}

	public void setState(UploadState uploadState) {
		this.uploadState = uploadState;
	}

	public IDocument getOrCreateTextDocument() {
		return textDocument == null ? (textDocument = new Document()) : textDocument;
	}

	public void setText(String text) {
		getOrCreateTextDocument().set(text);
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public String toString() {
		return String.format("Page [%s : %s] - %s", title, headline, uploadState);
	}
}