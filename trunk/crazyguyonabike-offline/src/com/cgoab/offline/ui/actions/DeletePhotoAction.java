package com.cgoab.offline.ui.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.PageNotEditableException;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;

public class DeletePhotoAction extends Action {
	private final ThumbnailViewer viewer;

	public DeletePhotoAction(final ThumbnailViewer viewer) {
		super("Remove");
		this.viewer = viewer;
		viewer.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Object[] selection = viewer.getSelection();
				if (selection.length == 0) {
					setEnabled(false);
				} else {
					boolean enabled = true;
					for (Object o : selection) {
						if (o instanceof Photo) {
							if (((Photo) o).getState() == UploadState.UPLOADED) {
								enabled = false;
								break;
							}
						}
					}
					setEnabled(enabled);
				}
			}
		});
	}

	@Override
	public void run() {
		Object[] selection = viewer.getSelection();
		Page page = JournalSelectionService.getInstance().getSelectedPage();
		if (page == null) {
			return;
		}
		List<Photo> photos = new ArrayList<Photo>(selection.length);
		for (Object s : selection) {
			photos.add((Photo) s);
		}
		try {
			page.removePhotos(photos);
		} catch (PageNotEditableException e) {
			/* ignore */
		}
	}
}
