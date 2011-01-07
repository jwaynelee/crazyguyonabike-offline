package com.cgoab.offline.ui;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.UploadState;

public class OpenPageInBrowserAction extends Action implements ISelectionChangedListener {

	private static final Logger LOG = LoggerFactory.getLogger(OpenPageInBrowserAction.class);

	private Page browsablePage;

	private Shell shell;

	public OpenPageInBrowserAction(TreeViewer treeViewer, Shell shell) {
		super("Open in Browser");
		treeViewer.addSelectionChangedListener(this);
		setEnabled(false);
		this.shell = shell;
	}

	@Override
	public void selectionChanged(SelectionChangedEvent event) {
		List elements = ((IStructuredSelection) event.getSelection()).toList();
		Page newPage = null;
		if (elements != null && elements.size() == 1 && elements.get(0) instanceof Page) {
			Page p = (Page) elements.get(0);
			if (p.getState() == UploadState.UPLOADED || p.getState() == UploadState.PARTIALLY_UPLOAD) {
				newPage = p;
			}
		}

		setEnabled(newPage != null);
		browsablePage = newPage;
	}

	private static String cgoabUrlForPage(int pageId) {
		return "http://www.crazyguyonabike.com/doc/page/?page_id=" + pageId;
	}

	public void run() {
		try {
			Desktop.getDesktop().browse(new URI(cgoabUrlForPage(browsablePage.getServerId())));
		} catch (Exception e) {
			LOG.warn("Failed to browse page", e);
			MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
			box.setMessage("Failed to open browser : " + e.toString());
			box.open();
		}
	}
}
