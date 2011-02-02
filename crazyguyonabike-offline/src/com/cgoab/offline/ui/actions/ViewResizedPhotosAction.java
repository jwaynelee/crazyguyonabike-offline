package com.cgoab.offline.ui.actions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

import org.eclipse.swt.program.Program;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;
import com.cgoab.offline.util.resizer.ResizerService;

public class ViewResizedPhotosAction extends ActionWithCurrentJournal {

	public ViewResizedPhotosAction() {
		super("View resized photos");
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
				setEnabled(enabled == Boolean.TRUE);
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
		Program.launch(folder.getAbsolutePath());
	}
}