package com.cgoab.offline.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.util.Assert;

/**
 * A page in a journal.
 * <p>
 * New pages are create unattached to a journal. Calling
 * {@link Journal#addPage(Page, int)} will bind the page to the journal. Events
 * on this page will then be broadcast to listeners on the journal.
 */
public class Page {

	public static final String BOLD = "bold";

	public static final String HEADING_STYLE = "headingStyle";

	public static final String HEADLINE = "headline";

	public static final String INDENT = "indent";

	public static final int INDENT_MAX = 10;

	public static final int INDENT_MIN = 1;

	public static final String ITALIC = "italic";

	private static final Logger LOGGER = LoggerFactory.getLogger(Page.class);

	public static final String STATE = "state";

	public static final String PHOTOS_ORDER = "photosOrder";

	public static final String TITLE = "title";

	public static final int UNSET_LOCAL_ID = -1;

	public static final int UNSET_SERVER_ID = -1;

	private static Comparator<Photo> getComparator(PhotosOrder order) {
		switch (order) {
		case NAME:
			return FileNameComparator.INSTANCE;
		case MANUAL:
			return null;
			// case DATE:
			// return FileDateComparator.INSTANCE;
		default:
			throw new IllegalArgumentException("Invalid order " + order);
		}
	}

	private boolean bold = false;

	private LocalDate date = new LocalDate(); // default to today

	private int distance;

	private EditFormat editFormat = EditFormat.AUTO;

	private HeadingStyle headingStyle = HeadingStyle.SMALL;

	private String headline;

	private int indent = 1;

	private boolean italic = false;

	private Journal journal;

	private PhotosOrder order = PhotosOrder.NAME;

	private final List<Photo> photos = new ArrayList<Photo>();

	private int serverId = UNSET_SERVER_ID;

	private final PropertyChangeSupport support = new PropertyChangeSupport(this);

	private IDocument textDocument;

	private String title;

	private UploadState uploadState = UploadState.NEW;

	private boolean visible = true;

	public Page() {
	}

	void bind(Journal journal) {
		Assert.isNull(this.journal);
		this.journal = journal;
	}

	void unbind() {
		this.journal = null;
	}

	/**
	 * Adds one or more photos to this page at a given location in the photos
	 * list.
	 * 
	 * @param photosToAdd
	 *            photos to be added
	 * @param insertionPoint
	 *            insertion point in photos list, <tt>-1</tt> can be used to
	 *            append to the end.
	 * @throws DuplicatePhotoException
	 * @throws InvalidInsertionPointException
	 * @throws PageNotEditableException
	 */
	public void addPhotos(List<Photo> photosToAdd, int insertionPoint) throws DuplicatePhotoException,
			InvalidInsertionPointException, PageNotEditableException {
		assertIsEditable();
		assertValidInsertionPoint(insertionPoint);

		/* check for duplicates */
		Map<Photo, Page> duplicates = new LinkedHashMap<Photo, Page>();
		Map<String, Page> journalPhotos = journal == null ? Collections.<String, Page> emptyMap() : journal
				.getPhotoMap();
		Set<Photo> nonDuplicatePhotos = new HashSet<Photo>();
		for (Photo photo : photosToAdd) {
			if (nonDuplicatePhotos.contains(photo)) {
				duplicates.put(photo, null);
			} else {
				/* photos identified by local name only */
				Page duplicate = journalPhotos.get(photo.getFile().getName());
				if (duplicate != null) {
					duplicates.put(photo, duplicate);
				} else {
					nonDuplicatePhotos.add(photo);
				}
			}
		}

		if (duplicates.size() > 0) {
			throw new DuplicatePhotoException(duplicates);
		}

		if (insertionPoint == -1) {
			photos.addAll(photosToAdd);
		} else {
			photos.addAll(insertionPoint, photosToAdd);
		}

		sortPhotos();

		if (journal != null) {
			journal.setDirty(true);
			journal.photosAdded(photosToAdd, this);
		}
	}

	private void sortPhotos() {
		// order photos if no photos have yet been uploaded
		if (uploadState == UploadState.NEW || uploadState == UploadState.ERROR) {
			Comparator<Photo> comparator = getComparator(order);
			if (comparator != null) {
				Collections.sort(photos, comparator);
			}
		} else {
			LOGGER.info("Not ordering photos as page is in state {}", uploadState);
		}
	}

	/**
	 * <b>WARNING</b> only certain properties fire callbacks (those with
	 * constant property names, eg, {@link #TITLE})! Implement as required.
	 * 
	 * @param listener
	 */
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		support.addPropertyChangeListener(listener);
	}

	private void assertIsEditable() throws PageNotEditableException {
		if (getState() == UploadState.UPLOADED) {
			throw new PageNotEditableException();
		}
	}

	private void assertPageOwnsPhotos(List<Photo> photosToCheck) {
		for (Photo photo : photosToCheck) {
			if (!photos.contains(photo)) {
				throw new IllegalArgumentException("Page does not own photo " + photo);
			}
		}
	}

	/**
	 * Returns <tt>true</tt> if the insertion point is after the last uploaded
	 * photo.
	 * 
	 * @param insertionPoint
	 * @return
	 * @throws InvalidInsertionPointException
	 */
	private void assertValidInsertionPoint(int insertionPoint) throws InvalidInsertionPointException {
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

	public LocalDate getDate() {
		return date;
	}

	public int getDistance() {
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

	public IDocument getOrCreateTextDocument() {
		return textDocument == null ? (textDocument = new Document()) : textDocument;
	}

	/**
	 * Returns an unmodifiable copy of the photos attached to this page ordered
	 * as specified by {@link #getPhotosOrder()}.
	 * 
	 * @return
	 */
	public List<Photo> getPhotos() {
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

	public boolean isBold() {
		return bold;
	}

	public boolean isItalic() {
		return italic;
	}

	public boolean isVisible() {
		return visible;
	}

	public void movePhotos(List<Photo> toMove, int insertionPoint) throws InvalidInsertionPointException,
			PageNotEditableException {
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

		// manually transition to manual order
		setPhotosOrder(PhotosOrder.MANUAL);
		if (journal != null) {
			journal.setDirty(true);
		}
	}

	public void removePhotos(List<Photo> toRemove) throws PageNotEditableException {
		assertIsEditable();
		assertPageOwnsPhotos(toRemove);

		// only remove NEW or ERROR photos
		for (Photo p : toRemove) {
			if (p.getState() != UploadState.NEW && p.getState() != UploadState.ERROR) {
				return; // TODO throw?
			}
		}

		photos.removeAll(toRemove);

		if (journal != null) {
			journal.setDirty(true);
			journal.photosRemoved(toRemove, this);
		}
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		support.removePropertyChangeListener(listener);
	}

	public void setBold(boolean bold) {
		support.firePropertyChange(BOLD, this.bold, this.bold = bold);
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public void setDistance(int distance) {
		this.distance = distance;
	}

	public void setFormat(EditFormat editFormat) {
		this.editFormat = editFormat;
	}

	public void setHeadingStyle(HeadingStyle headingStyle) {
		Assert.notNull(headingStyle);
		support.firePropertyChange(HEADING_STYLE, this.headingStyle, this.headingStyle = headingStyle);
	}

	public void setHeadline(String headline) {
		support.firePropertyChange(HEADLINE, this.headline, this.headline = headline);
	}

	public void setIndent(int indent) {
		if (indent > INDENT_MAX) {
			throw new IndexOutOfBoundsException("indent must be <= " + INDENT_MAX);
		}
		if (indent < INDENT_MIN) {
			throw new IndexOutOfBoundsException("indent must be >= " + INDENT_MIN);
		}
		support.firePropertyChange(INDENT, this.indent, this.indent = indent);
	}

	public void setItalic(boolean italic) {
		support.firePropertyChange(ITALIC, this.italic, this.italic = italic);
	}

	void setPhotos(List<Photo> p) {
		photos.addAll(p);
		if (journal != null) {
			journal.photosAdded(p, this);
		}
	}

	public void setPhotosOrder(PhotosOrder newOrder) {
		PhotosOrder oldOrder = this.order;
		this.order = newOrder;
		sortPhotos();
		support.firePropertyChange(PHOTOS_ORDER, oldOrder, newOrder);
	}

	public void setServerId(int id) {
		this.serverId = id;
	}

	public void setState(UploadState newState) {
		support.firePropertyChange(STATE, this.uploadState, this.uploadState = newState);
	}

	public void setText(String text) {
		getOrCreateTextDocument().set(text);
	}

	public void setTitle(String title) {
		support.firePropertyChange(TITLE, this.title, this.title = title);
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public String toShortString() {
		return title + (headline == null ? "" : (" : " + headline));
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder("Page ");
		b.append("[").append(title);
		if (headline != null) {
			b.append(" : ").append(headline);
		}
		b.append("]");
		b.append(" - ").append(uploadState);
		return b.toString();
	}

	public static enum EditFormat {
		AUTO, LIST, MANUAL
	}

	public static enum HeadingStyle {
		LARGE, MEDIUM, SMALL
	}

	public static enum PhotosOrder {
		/*
		 * DATE, -- removed as order done before meta loaded and file-mod date
		 * is inacurate, file name is simpler way to derive order
		 */
		MANUAL, NAME;
	}
}