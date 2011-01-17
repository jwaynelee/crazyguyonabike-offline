package com.cgoab.offline.ui.actions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.eclipse.jface.action.Action;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;
import com.cgoab.offline.ui.ThumbnailView;

public class ToggleResizeImagesBeforeUpload extends Action {

	private final ThumbnailView thumbView;

	public ToggleResizeImagesBeforeUpload(ThumbnailView thumbnailView) {
		super("Resize photos", Action.AS_CHECK_BOX);
		this.thumbView = thumbnailView;
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {

			PropertyChangeListener listener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (Journal.RESIZE_IMAGES_BEFORE_UPLOAD.equals(evt.getPropertyName())) {
						Boolean resize = (Boolean) evt.getNewValue();
						setChecked(resize == Boolean.TRUE);
						setEnabled(resize != null);
					}
				}
			};

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
			}

			@Override
			public void journalOpened(Journal journal) {
				setChecked(journal.isResizeImagesBeforeUpload() == Boolean.TRUE);
				setEnabled(journal.isResizeImagesBeforeUpload() != null);
				journal.addPropertyChangeListener(listener);
			}

			@Override
			public void journalClosed(Journal journal) {
				setChecked(false);
				setEnabled(false);
				journal.removePropertyChangeListener(listener);
			}
		});
	}

	public void run(Journal journal) {
		Boolean currentSetting = journal.isResizeImagesBeforeUpload();
		if (currentSetting == null || currentSetting != isChecked()) {
			if (isChecked()) {
				if (!thumbView.registerResizer(journal, false)) {
					return;
				}
			} else {
				thumbView.unregisterResizer(journal);
			}
			journal.setResizeImagesBeforeUpload(isChecked());
		}
	}
}
