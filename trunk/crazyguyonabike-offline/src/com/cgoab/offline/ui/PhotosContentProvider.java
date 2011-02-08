package com.cgoab.offline.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.InvalidInsertionPointException;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.PageNotEditableException;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewerContentProvider;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewerEventListener;

/**
 * Links thumbnail viewer to model
 */
public class PhotosContentProvider implements ThumbnailViewerContentProvider, ThumbnailViewerEventListener {

	private static final Logger LOG = LoggerFactory.getLogger(PhotosContentProvider.class);

	public static List<Photo> toPhotoList(Object[] os) {
		List<Photo> photos = new ArrayList<Photo>(os.length);
		for (Object o : os) {
			photos.add((Photo) o);
		}
		return photos;
	}

	private Page currentPage;

	private Shell shell;

	private ThumbnailView view;

	private ThumbnailViewer viewer;

	public PhotosContentProvider(Shell shell, ThumbnailView view) {
		this.shell = shell;
		this.view = view;
	}

	// TODO review if insertion point should be an absolute position and not
	// from filtered list
	private int adjustInsertionPointForHiddenPhotos(int point) {
		if (!currentPage.getJournal().isHideUploadedContent()) {
			return point;
		}

		// add index of last UPLOADED photo
		List<Photo> photos = currentPage.getPhotos();
		int lastUploaded = 0;
		for (; lastUploaded < photos.size(); ++lastUploaded) {
			Photo p = photos.get(lastUploaded);
			if (p.getState() != UploadState.UPLOADED) {
				break;
			}
		}

		return lastUploaded + point;
	}

	@Override
	public void dispose() {
		if (viewer != null) {
			viewer.removeEventListener(this);
		}
	}

	@Override
	public Object[] getThumbnails(Object input) {
		return ((Page) input).getPhotos().toArray(new Photo[0]);
	}

	@Override
	public void inputChanges(ThumbnailViewer viewer, Object oldInput, Object newInput) {
		this.viewer = viewer;
		currentPage = (Page) newInput;
		viewer.addEventListener(this); // no-op if already registered
	}

	@Override
	public boolean itemFailedToLoad(Object image, Throwable exception) {
		Photo photo = (Photo) image;
		LOG.warn("Failed to load thumbnail for [" + photo.getFile().getAbsolutePath() + "]", exception);
		if (photo.getState() == UploadState.UPLOADED) {
			/* ignore, it is already uploaded, nothing we can do */
			return true;
		}
		if (MessageDialog.openQuestion(shell, "Failed to load image", "Failed to load image " + photo.getFile()
				+ " due to:\n\n" + exception.getMessage() + "\n\nDo you want to keep this photo?")) {
			/* YES = keep */
			return true;
		} else {
			/* NO = remove */
			try {
				currentPage.removePhotos(Arrays.asList(photo));
				currentPage.getJournal().setDirty(true);
			} catch (PageNotEditableException e) {
				/* ignore */
			}
			return false;
		}
	}

	@Override
	public void itemsAdded(File[] newItems, int insertionPoint) {
		view.addPhotosRetryIfDuplicates(newItems, adjustInsertionPointForHiddenPhotos(insertionPoint));
	}

	@Override
	public void itemsMoved(Object[] selection, int insertionPoint) {
		// refresh not necessary as binding to sort button above will trigger
		try {
			currentPage.movePhotos(toPhotoList(selection), adjustInsertionPointForHiddenPhotos(insertionPoint));
		} catch (InvalidInsertionPointException e) {
			/* ignore */
			return;
		} catch (PageNotEditableException e) {
			/* ignore */
			return;
		}
		viewer.refresh();
	}

	@Override
	public void itemsRemoved(Object[] selection) {
		StringBuilder msg = new StringBuilder("Are you sure you want to remove these ").append(selection.length)
				.append(" photo(s):\n\n");
		for (int i = 0; i < selection.length; ++i) {
			if (i == 10) {
				msg.append("   ... ").append(selection.length - i).append(" more\n");
				break;
			}
			Photo p = (Photo) selection[i];
			msg.append("   '").append(p.getFile().getName()).append("'\n");
		}

		if (MessageDialog.openQuestion(shell, "Confirm delete", msg.toString())) {
			try {
				currentPage.removePhotos(toPhotoList(selection));
			} catch (PageNotEditableException e) {
				return; /* ignore */
			}
		}
	}

	// @Override
	// public void itemMetaDataAvailable(Object data, Metadata meta) {
	// // verify the photo just added was actually taken on the same day,
	// // and notify the user if not
	// Date date = getPhotoDateTime(meta);
	// if (date == null) {
	// return;
	// }
	//
	// LocalDate pageDate = currentPage.getDate();
	// LocalDate photoDate = LocalDate.fromDateFields(date);
	// if (!pageDate.isEqual(photoDate)) {
	// MessageBox box = new MessageBox(editor.getShell(), SWT.ICON_WARNING |
	// SWT.YES | SWT.NO);
	// box.setMessage("Photo was taken on a different date to the page, do you want to remove this photo?");
	// if (box.open() == SWT.YES) {
	// /* remove */
	// viewer.remove(new Object[] { data });
	// } else {
	// /* keep */
	// }
	// }
	// }
}