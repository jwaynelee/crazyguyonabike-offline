package com.cgoab.offline.ui.actions;

import java.io.File;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.ui.thumbnailviewer.ExifViewer;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.drew.metadata.Metadata;

public class OpenPhotoInfoAction extends Action {

	private final ThumbnailViewer viewer;
	private Shell shell;

	public OpenPhotoInfoAction(Shell shell, final ThumbnailViewer viewer) {
		super("Info");
		this.viewer = viewer;
		viewer.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				setEnabled(viewer.getSelection().length == 1);
			}
		});
	}

	@Override
	public void run() {
		Object[] selected = viewer.getSelection();
		if (selected.length != 1) {
			return;
		}
		File f = ((Photo) selected[0]).getFile();
		Journal journal = JournalSelectionService.getInstance().getCurrentJournal();
		if (journal == null) {
			return;
		}
		Metadata meta = CachingThumbnailProviderFactory.getProvider(journal).getOrLoadMetaData(f);
		if (meta == null) {
			MessageDialog.openError(shell, "No metadata available", "No metadata is available the image");
		} else {
			new ExifViewer(shell).open(meta, f);
		}
	}
}
