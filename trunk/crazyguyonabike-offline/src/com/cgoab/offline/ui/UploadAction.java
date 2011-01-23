package com.cgoab.offline.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
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
import com.cgoab.offline.util.Assert;
import com.cgoab.offline.util.resizer.ResizerService;

/**
 * Opens an {@link UploadDialog} and updates the UI as appropriate.
 */
public class UploadAction extends Action {

	private UploadClientFactory factory;
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
		if (PreferenceUtils.getStore().getBoolean(PreferenceUtils.USE_MOCK_CLIENT)) {
			return new MockClient();
		} else {
			return factory.newClient();
		}
	}

	@Override
	public void run() {
		Journal journal = JournalSelectionService.getInstance().getCurrentJournal();
		JournalUtils.saveJournal(journal, false, shell);

		/* 1) filter pages to upload, prompt if failed upload found */
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

		if (newPages.size() == 0) {
			return;
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

		/* 2) block until all photos in journal are resized */
		ResizerService service = (ResizerService) journal.getData(ResizerService.KEY);
		if (!JournalUtils.blockUntilPhotosResized(service, shell)) {
			return; // wait cancelled
		}

		/* 3) validate upload */
		int idOfFirstPage = journal.getPages().indexOf(newPages.get(0));
		Assert.isTrue(idOfFirstPage != -1);

		Page previous = null;
		if (idOfFirstPage > 0) {
			previous = journal.getPages().get(idOfFirstPage - 1);
		}

		List<String> errors = new ArrayList<String>();
		for (Page page : newPages) {
			/* make sure date is newer than previous */
			if (previous != null) {
				if (previous.getDate().isAfter(page.getDate())) {
					errors.add("[" + page.getTitle() + "] date " + page.getDate() + " is earlier than previous page ["
							+ previous.getTitle() + "] date " + previous.getDate());
				}
			}

			for (Photo photo : page.getPhotos()) {
				File resizedPhoto = photo.getResizedPhoto();
				if (resizedPhoto != null) {
					if (resizedPhoto.exists()) {
						/* ok */
					} else {
						errors.add("Resized photo [" + resizedPhoto.getAbsolutePath() + "] does not exist");
					}
				} else if (!photo.getFile().exists()) {
					errors.add("Photo [" + photo.getFile().getAbsolutePath() + "] does not exist ");
				}
			}
			previous = page;
		}

		if (errors.size() > 0) {
			StringBuffer str = new StringBuffer(
					"Errors were detected in the pages you are attempting to upload, please fix these and try again.\n\n");
			for (String error : errors) {
				str.append("  - ").append(error).append("\n");
			}
			MessageDialog.openError(shell, "Errors detected before upload", str.toString());
			return;
		}

		/* 4) ask if pages should be made visible */
		// TODO use MessageDialogWithToggle when preferences in place
		boolean visible;
		if (!PreferenceUtils.getStore().contains(PreferenceUtils.NEW_PAGES_VISIBLE)) {
			visible = MessageDialogWithToggle.openYesNoQuestion(shell, "Make new pages visible?",
					"Do you want these uploaded pages to be visible?", "always do this?", false,
					PreferenceUtils.getStore(), PreferenceUtils.NEW_PAGES_VISIBLE).getReturnCode() == IDialogConstants.YES_ID;
		} else {
			visible = PreferenceUtils.getStore().getString(PreferenceUtils.NEW_PAGES_VISIBLE) == MessageDialogWithToggle.ALWAYS;
		}

		// HACK - no option in UI to set visible, so set now
		for (Page page : newPages) {
			page.setVisible(visible);
		}

		UploadDialog dialog = new UploadDialog(shell);
		dialog.setThumbnailProvider((ThumbnailProvider) journal.getData(ThumbnailProvider.KEY));
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

	public void setUploadFactory(UploadClientFactory factory) {
		this.factory = factory;
	}
}
