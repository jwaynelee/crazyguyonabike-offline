package com.cgoab.offline.ui.actions;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider;
import com.cgoab.offline.util.Utils;

public class PurgeThumbnailCacheAction extends ActionWithCurrentJournal {

	private final Shell shell;

	public PurgeThumbnailCacheAction(Shell shell) {
		super("Purge thumbnail cache");
		this.shell = shell;
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {
			@Override
			public void journalClosed(Journal journal) {
				setEnabled(false);
			}

			@Override
			public void journalOpened(Journal journal) {
				setEnabled(true);
			}

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
			}
		});
	}

	@Override
	public void run(Journal journal) {
		CachingThumbnailProvider provider = (CachingThumbnailProvider) journal.getData(ThumbnailProvider.KEY);
		if (provider == null) {
			return;
		}
		long bytes = provider.purge();
		if (bytes > 0) {
			MessageDialog.openInformation(shell, "Thumbnails purged", Utils.formatBytes(bytes)
					+ " of thumbnails deleted");
		}
	}
}
