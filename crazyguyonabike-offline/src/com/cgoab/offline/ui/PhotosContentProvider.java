package com.cgoab.offline.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.omg.CORBA.UserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.DuplicatePhotoException;
import com.cgoab.offline.model.InvalidInsertionPointException;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.PageNotEditableException;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewerContentProvider;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewerEventListener;
import com.cgoab.offline.util.Assert;
import com.cgoab.offline.util.resizer.ImageMagickResizeTask.MagicNotAvailableException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectory;

/**
 * Links thumbnail viewer to model
 */
public class PhotosContentProvider implements ThumbnailViewerContentProvider, ThumbnailViewerEventListener {

	private static final Logger LOG = LoggerFactory.getLogger(PhotosContentProvider.class);

	private ThumbnailViewer viewer;

	private Page currentPage;

	private PageEditor editor;

	public PhotosContentProvider(PageEditor editor) {
		this.editor = editor;
	}

	@Override
	public void inputChanges(ThumbnailViewer viewer, Object oldInput, Object newInput) {
		this.viewer = viewer;
		currentPage = (Page) newInput;
		viewer.addEventListener(this); // no-op if already registered
	}

	@Override
	public Object[] getThumbnails(Object input) {
		return ((Page) input).getPhotos().toArray(new Photo[0]);
	}

	@Override
	public void dispose() {
		if (viewer != null) {
			viewer.removeEventListener(this);
		}
	}

	@Override
	public boolean itemFailedToLoad(Object image, Throwable exception) {
		Photo photo = (Photo) image;
		LOG.warn("Failed to load thumbnail for [" + photo.getFile().getAbsolutePath() + "]", exception);
		// prompt the user to continue with an invalid image or remove...
		if (photo.getState() == UploadState.UPLOADED) {
			// ignore, it is already uploaded, nothing we can do
			return true;
		}
		MessageBox error = new MessageBox(editor.getShell(), SWT.ICON_ERROR | SWT.YES | SWT.NO);
		error.setText("Failed to load image");
		error.setMessage("Failed to load image " + photo.getFile() + " due to:\n\n" + exception.getMessage()
				+ "\n\nDo you want to keep this photo?");
		if (error.open() == SWT.YES) {
			// remove
			return true;
		} else {
			try {
				currentPage.removePhotos(Arrays.asList(photo));
			} catch (PageNotEditableException e) {
				/* ignore */
			}
			currentPage.getJournal().setDirty(true);
			return false;
		}
	}

	public static List<Photo> toPhotoList(Object[] os) {
		List<Photo> photos = new ArrayList<Photo>(os.length);
		for (Object o : os) {
			photos.add((Photo) o);
		}
		return photos;
	}

	@Override
	public void itemsRemoved(Object[] selection) {
		MessageBox box = new MessageBox(editor.getShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		box.setText("Confirm delete");
		StringBuilder b = new StringBuilder("Are you sure you want to remove these ").append(selection.length).append(
				" photo(s):\n\n");
		for (int i = 0; i < selection.length; ++i) {
			if (i == 10) {
				b.append("   ... ").append(selection.length - i).append(" more\n");
				break;
			}
			Photo p = (Photo) selection[i];
			b.append("   '").append(p.getFile().getName()).append("'\n");
		}

		box.setMessage(b.toString());
		if (box.open() != SWT.YES) {
			return;
		}
		try {
			currentPage.removePhotos(toPhotoList(selection));
			viewer.remove(selection);
		} catch (PageNotEditableException e) {
			return; /* ignore */
		}
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
		// HACK: manually refresh the page to reflect new order
		editor.selectOrderByButton(currentPage.getPhotosOrder());
		viewer.refresh();
	}

	public void addPhotosRetryIfDuplicates(File[] files, int insertionPoint) {
		List<Photo> photos = new ArrayList<Photo>(files.length);
		for (File f : files) {
			photos.add(new Photo(f));
		}

		try {
			currentPage.addPhotos(photos, insertionPoint);
		} catch (InvalidInsertionPointException e) {
			return;
		} catch (PageNotEditableException e) {
			return;
		} catch (DuplicatePhotoException e) {
			StringBuilder b = new StringBuilder("The following photos are already added to this journal:\n\n");
			int i = 0;
			Map<Photo, Page> duplicates = e.getDuplicatePhotos();
			for (Iterator<Entry<Photo, Page>> it = duplicates.entrySet().iterator(); it.hasNext();) {
				Entry<Photo, Page> next = it.next();
				if (i++ == 10) {
					b.append("  ").append("... ").append(duplicates.size() - i).append(" more\n");
					break;
				}
				b.append("  ").append(next.getKey().getFile().getName());
				if (next.getValue() == null) {
					b.append("\n");
				} else if (next.getValue() == currentPage) {
					b.append(" (this page)\n");
				} else {
					b.append(" (page " + next.getValue().getTitle() + ")\n");
				}
			}
			b.append("\nIf you need to attach a duplicate photo, copy to a new file first.");
			int nonDuplicatePhotos = photos.size() - duplicates.size();
			int style = SWT.ICON_WARNING;
			if (nonDuplicatePhotos > 0) {
				b.append("Do you want to continue with the duplicates removed?");
				style |= SWT.YES | SWT.NO;
			} else {
				style |= SWT.OK;
			}
			MessageBox msg = new MessageBox(editor.getShell(), style);
			msg.setText("Duplicate image(s) detected");
			msg.setMessage(b.toString());
			if (msg.open() != SWT.YES) {
				return;
			}
			photos.removeAll(duplicates.keySet());
			try {
				currentPage.addPhotos(photos, insertionPoint);
			} catch (DuplicatePhotoException e1) {
				return; /* can't happen! */
			} catch (InvalidInsertionPointException e1) {
				return; /* ignore */
			} catch (PageNotEditableException e1) {
				return; /* ignore */
			}
		}

		/* before refresh, ask if EXIF thumbnails should be used */
		Journal journal = currentPage.getJournal();
		if (journal.isUseExifThumbnail() == null) {
			MessageBox box = new MessageBox(editor.getShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
			box.setText("Use embedded thumnails?");
			box.setMessage("Do you want to use embedded JPEG thumbnails if available?\n\n"
					+ "Embedded thumnbails load quicker but may be of poor quality. If you"
					+ " don't use them a new thumbnail will be created for each photo, "
					+ "providing crisper thumbnails but taking longer to load.");
			CachingThumbnailProvider provider = editor.getThumbnailProviderFactory().getThumbnailProvider(journal);
			boolean useExif = box.open() == SWT.YES;
			provider.setUseExifThumbnail(useExif);
			journal.setUseExifThumbnail(useExif);
			editor.toggleUseExifThumbnailAction.setChecked(useExif);
		}

		viewer.refresh();
		viewer.setSelection(new StructuredSelection(photos), true);

		Boolean resize = journal.isResizeImagesBeforeUpload();
		if (resize == null) {
			MessageBox box = new MessageBox(editor.getShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
			box.setText("Resize photos?");
			box.setMessage("Do you always want photos added to this journal to be resized before uploading?");
			switch (box.open()) {
			case SWT.YES:
				resize = Boolean.TRUE;
				break;
			case SWT.NO:
				resize = Boolean.FALSE;
				break;
			}

			if (resize != null) {
				if (resize == Boolean.TRUE) {
					// from no one any new photos will be auto-resized
					if (!editor.registerPhotoResizer(journal, false)) {
						return;
					}
				}
				journal.setResizeImagesBeforeUpload(resize);
				editor.toggleResizePhotos.setChecked(true);
			}
		}
	}

	@Override
	public void itemsAdded(File[] newItems, int insertionPoint) {
		addPhotosRetryIfDuplicates(newItems, adjustInsertionPointForHiddenPhotos(insertionPoint));
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