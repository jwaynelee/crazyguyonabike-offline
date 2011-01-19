package com.cgoab.offline.ui.actions;

import java.awt.Desktop;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;
import com.cgoab.offline.util.resizer.ResizerService;

public class ViewResizedPhotosAction extends ActionWithCurrentJournal {

	private static Logger LOG = LoggerFactory.getLogger(ViewResizedPhotosAction.class);

	public ViewResizedPhotosAction() {
		super("View resized photos");
		setEnabled(Desktop.isDesktopSupported());
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {

			PropertyChangeListener propertyListener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (Journal.RESIZE_IMAGES_BEFORE_UPLOAD.equals(evt.getPropertyName())) {
						syncWithJournal((Boolean) evt.getNewValue());
					}
				}
			};

			@Override
			public void journalClosed(Journal journal) {
				setEnabled(false);
			}

			@Override
			public void journalOpened(Journal journal) {
				journal.addPropertyChangeListener(propertyListener);
				syncWithJournal(journal.isResizeImagesBeforeUpload());
			}

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
			}

			private void syncWithJournal(Boolean enabled) {
				setEnabled(enabled == Boolean.TRUE && Desktop.isDesktopSupported());
			}
		});
	}

	@Override
	public void run(Journal journal) {
		ResizerService resizer = (ResizerService) journal.getData(ResizerService.KEY);
		if (resizer == null) {
			return;
		}
		File folder = resizer.getPhotoFolder();
		try {
			Desktop.getDesktop().open(folder);
		} catch (IOException e) {
			/* ignore */
			LOG.debug("Failed to browse folder " + folder);
		}
	}
}