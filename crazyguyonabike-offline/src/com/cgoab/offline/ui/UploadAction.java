package com.cgoab.offline.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.client.UploadClient;
import com.cgoab.offline.client.UploadClientFactory;
import com.cgoab.offline.client.mock.MockClient;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.ui.UploadDialog.UploadResult;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.util.resizer.ResizerService;

/**
 * Opens an {@link UploadDialog} and updates the UI as appropriate.
 */
public class UploadAction extends Action {

	private UploadClientFactory factory;
	private Preferences preferences;
	private Shell shell;
	private ThumbnailViewer thumbnails;
	private TreeViewer tree;

	public UploadAction(Shell shell, ThumbnailViewer thumbnails, TreeViewer tree) {
		super("Upload Journal");
		this.shell = shell;
		this.thumbnails = thumbnails;
		this.tree = tree;
	}

	public void afterUpload(Page errorPage, Photo errorPhoto) {
		/* refresh to get new labels (updated/error) */
		tree.refresh();

		if (errorPage != null) {
			// selection event triggers refresh of thumnail viewer
			tree.setSelection(new StructuredSelection(errorPage), true);

			if (errorPage == JournalSelectionService.getInstance().getSelectedPage()) {
				// HACK: force refresh of thumbnail viewer
				thumbnails.refresh();
			}

			if (errorPhoto != null) {
				thumbnails.setSelection(new StructuredSelection(errorPhoto), true);
			}
		}
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
		Journal journal = JournalSelectionService.getInstance().getCurrentJournal();
		JournalUtils.saveJournal(journal, false, shell);

		/* 1) block until all photos in journal are resized */
		ResizerService service = (ResizerService) journal.getData(ResizerService.KEY);
		if (!JournalUtils.blockUntilPhotosResized(service, shell)) {
			return; // wait cancelled
		}

		UploadDialog dialog = new UploadDialog(shell);
		dialog.setThumbnailProvider((ThumbnailProvider) journal.getData(ThumbnailProvider.KEY));

		/* 2) find pages to upload, prompty if partial upload */
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
			String msg;
			if (foundErrorPage) {
				msg = "You are attempting to upload a page that previously failed to upload, do you want to continue?";
			} else {
				msg = "You are attempting to upload a page with photo(s) that previously failed to upload, do you want to continue?";
			}

			if (!MessageDialog.openConfirm(shell, "Confirm upload", msg)) {
				return;
			}
		}

		/* 3) ask if pages should be made visible */
		// TODO use MessageDialogWithToggle when preferences in place
		boolean visible = MessageDialog.openQuestion(shell, "Make new pages visible?",
				"Do you want these uploaded pages to be visible?");

		// HACK - no option in UI to set visible, so set now
		for (Page page : newPages) {
			page.setVisible(visible);
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
		JournalUtils.saveJournal(journal, false, shell);

		// notify editor: uploaded pages are removed & error labels applied
		if (result.isComplete()) {
			afterUpload(null, null);
		} else {
			afterUpload(result.getLastPage(), result.getLastPhoto());
		}
	}

	public void setPreferences(Preferences preferences) {
		this.preferences = preferences;
	}

	public void setUploadFactory(UploadClientFactory factory) {
		this.factory = factory;
	}
}
