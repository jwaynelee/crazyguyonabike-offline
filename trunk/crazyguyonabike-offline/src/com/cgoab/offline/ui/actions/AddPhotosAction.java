package com.cgoab.offline.ui.actions;

import java.io.File;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;
import com.cgoab.offline.ui.PhotosContentProvider;
import com.cgoab.offline.ui.ThumbnailView;

public class AddPhotosAction extends Action {

	private final Shell shell;

	private final ThumbnailView thumbnailView;

	public AddPhotosAction(Shell shell, ThumbnailView thumbnailView) {
		super("Add Photos");
		this.shell = shell;
		this.thumbnailView = thumbnailView;
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
				boolean enabled = false;
				if (newSelection instanceof Page) {
					Page p = ((Page) newSelection);
					enabled = p.getState() != UploadState.UPLOADED;
				}
				setEnabled(enabled);
			}

			@Override
			public void journalOpened(Journal journal) {
			}

			@Override
			public void journalClosed(Journal journal) {
				setEnabled(false);
			}
		});
	}

	public void run() {
		if (JournalSelectionService.getInstance().getSelectedPage() == null) {
			return;
		}

		FileDialog dialog = new FileDialog(shell);
		dialog.setText("Select photos to add to page");
		dialog.setFileName("");
		dialog.open();
		String[] fileStrings = dialog.getFileNames();
		if (fileStrings.length == 0) {
			return;
		}

		// add photos to the model, manually trigger refresh of the viewer
		File[] files = new File[fileStrings.length];
		for (int i = 0; i < fileStrings.length; ++i) {
			files[i] = new File(dialog.getFilterPath() + File.separator + fileStrings[i]);
		}

		// update the model via view controller to apply the same error
		// handling for duplicates etc..
		thumbnailView.addPhotosRetryIfDuplicates(files, -1);
	}
}
