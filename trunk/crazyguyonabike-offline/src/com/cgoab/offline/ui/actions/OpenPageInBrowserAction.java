package com.cgoab.offline.ui.actions;

import java.awt.Desktop;
import java.net.URI;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;

public class OpenPageInBrowserAction extends Action {

	private static final Logger LOG = LoggerFactory.getLogger(OpenPageInBrowserAction.class);

	private static String cgoabUrlForPage(int pageId) {
		return "http://www.crazyguyonabike.com/doc/page/?page_id=" + pageId;
	}

	private final Shell shell;

	public OpenPageInBrowserAction(Shell shell) {
		super("Open in Browser");
		this.shell = shell;
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {
			@Override
			public void journalClosed(Journal journal) {
				setEnabled(false);
			}

			@Override
			public void journalOpened(Journal journal) {
			}

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
				boolean enabled = false;
				if (newSelection instanceof Page) {
					Page p = (Page) newSelection;
					enabled = p.getState() == UploadState.UPLOADED || p.getState() == UploadState.PARTIALLY_UPLOAD;
				}
				setEnabled(enabled);
			}
		});
	}

	@Override
	public void run() {
		try {
			Page page = JournalSelectionService.getInstance().getSelectedPage();
			Desktop.getDesktop().browse(new URI(cgoabUrlForPage(page.getServerId())));
		} catch (Exception e) {
			LOG.warn("Failed to browse page", e);
			MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
			box.setMessage("Failed to open browser : " + e.toString());
			box.open();
		}
	}
}
