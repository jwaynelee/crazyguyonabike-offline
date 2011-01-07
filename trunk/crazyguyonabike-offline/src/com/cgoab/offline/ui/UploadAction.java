package com.cgoab.offline.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.client.UploadClient;
import com.cgoab.offline.client.UploadClientFactory;
import com.cgoab.offline.client.mock.MockClient;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.ui.UploadDialog.UploadResult;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;

/**
 * Opens an {@link UploadDialog} and updates the UI as appropriate.
 */
public class UploadAction extends Action {

	private PageEditor editor;
	private Shell shell;
	private Preferences preferences;
	private CachingThumbnailProviderFactory thumbnailFactory;
	private ImageMagickResizerServiceFactory resizerFactory;
	private UploadClientFactory factory;

	public UploadAction(Shell shell, PageEditor editor) {
		super("Upload Journal");
		this.shell = shell;
		this.editor = editor;
	}

	public void setUploadFactory(UploadClientFactory factory) {
		this.factory = factory;
	}

	public void setThumbnailFactory(CachingThumbnailProviderFactory thumbnailFactory) {
		this.thumbnailFactory = thumbnailFactory;
	}

	public void setResizerFactory(ImageMagickResizerServiceFactory resizerFactory) {
		this.resizerFactory = resizerFactory;
	}

	public void setPreferences(Preferences preferences) {
		this.preferences = preferences;
	}

	private UploadClient createClient(Journal journal) {
		if (Boolean.valueOf(preferences.getValue(Preferences.USE_MOCK_IMPL_PATH))) {
			return new MockClient();
		} else {
			return factory.newClient();
		}
	}

	@Override
	public void run() {

		Object o = editor.getSelectedJournalOrPage();
		if (o == null || !(o instanceof Journal)) {
			return;
		}
		Journal journal = (Journal) o;
		editor.saveJournal(journal, false);

		// block until all photos in journal are resized
		if (editor.blockUntilPhotosResized(journal)) {
			return; // user cancelled the wait
		}

		UploadDialog dialog = new UploadDialog(shell);
		dialog.setThumbnailProvider(thumbnailFactory.getOrCreateThumbnailProvider(journal));

		/**
		 * Filter out already uploaded pages...
		 */
		List<Page> newPages = new ArrayList<Page>();
		boolean foundErrorPage = false;
		boolean foundPartialPage = false;
		for (Page page : journal.getPages()) {
			switch (page.getState()) {
			case UPLOADED:
				continue;
			case ERROR:
				foundErrorPage = true;
				break;
			case PARTIALLY_UPLOAD:
				foundPartialPage = true;
				break;
			case NEW:
			}
			newPages.add(page);
		}

		if (foundErrorPage || foundPartialPage) {
			MessageBox warn = new MessageBox(shell, SWT.ICON_WARNING | SWT.YES | SWT.NO);
			warn.setText("Confirm upload");
			if (foundErrorPage) {
				warn.setMessage("You are attempting to upload a page that previously failed to upload, do you want to continue?");
			} else {
				warn.setMessage("You are attempting to upload a page with photo(s) that previously failed to upload, do you want to continue?");
			}

			if (warn.open() == SWT.NO) {
				return;
			}
		}

		/**
		 * Prepare for upload, make sure each photo exists and resize (if
		 * required)...
		 */

		dialog.setPages(newPages);
		dialog.setJournal(journal);

		UploadClient client = null;
		UploadResult result;
		try {
			client = createClient(journal);
			dialog.setUploadClient(client);
			result = dialog.open();
		} finally {
			if (client != null) {
				client.dispose();
			}
		}

		if (result == null) {
			return; // nothing uploaded
		}

		// HACK: manually "dirty" journal, should probably be done by the model?
		journal.setDirty(true);

		// save the new upload state of pages & photos
		editor.saveJournal(journal, false);

		// notify editor: uploaded pages are removed & error labels applied
		if (result.isComplete()) {
			editor.afterUpload(null, null);
		} else {
			editor.afterUpload(result.getLastPage(), result.getLastPhoto());
		}
	}
}
