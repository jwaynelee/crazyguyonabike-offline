package com.cgoab.offline.ui.actions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;
import com.cgoab.offline.util.Utils;
import com.cgoab.offline.util.resizer.ResizerService;

public class PurgeResizedPhotosAction extends ActionWithCurrentJournal {

	private final Shell shell;

	public PurgeResizedPhotosAction(Shell shell) {
		super(("Purge resized photos"));
		this.shell = shell;
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {

			PropertyChangeListener listener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (Journal.RESIZE_IMAGES_BEFORE_UPLOAD.equals(evt.getPropertyName())) {
						setEnabled(evt.getNewValue() == Boolean.TRUE);
					}
				}
			};

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
			}

			@Override
			public void journalOpened(Journal journal) {
				setEnabled(journal.isResizeImagesBeforeUpload() == Boolean.TRUE);
				journal.addPropertyChangeListener(listener);
			}

			@Override
			public void journalClosed(Journal journal) {
				setEnabled(false);
			}
		});
	}

	public void run(Journal journal) {
		ResizerService service = (ResizerService) journal.getData(ResizerService.KEY);
		if (service == null) {
			return;
		}
		long bytes = service.purge();
		if (bytes > 0) {
			MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
			box.setText("Photos purged");
			box.setMessage(Utils.formatBytes(bytes) + " of resized photos deleted");
			box.open();
		}
	}
}
