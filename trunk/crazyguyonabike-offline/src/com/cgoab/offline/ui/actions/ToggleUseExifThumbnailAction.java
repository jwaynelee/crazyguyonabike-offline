package com.cgoab.offline.ui.actions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.eclipse.jface.action.Action;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;

public class ToggleUseExifThumbnailAction extends ActionWithCurrentJournal {

	CachingThumbnailProviderFactory factory;

	public ToggleUseExifThumbnailAction(CachingThumbnailProviderFactory factory) {
		super("Use EXIF thumbnail", Action.AS_CHECK_BOX);
		this.factory = factory;
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {

			PropertyChangeListener listener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (Journal.USE_EXIF_THUMBNAIL.equals(evt.getPropertyName())) {
						Boolean use = (Boolean) evt.getNewValue();
						setEnabled(use != null);
						setChecked(use == Boolean.TRUE);
					}
				}
			};

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
			}

			@Override
			public void journalOpened(Journal journal) {
				setEnabled(journal.isUseExifThumbnail() != null);
				setChecked(journal.isUseExifThumbnail() == Boolean.TRUE);
				journal.addPropertyChangeListener(listener);
			}

			@Override
			public void journalClosed(Journal journal) {
				setEnabled(false);
				setChecked(false);
				journal.removePropertyChangeListener(listener);
			}
		});
	}

	@Override
	public void run(Journal journal) {
		journal.setUseExifThumbnail(isChecked());
	}
}
