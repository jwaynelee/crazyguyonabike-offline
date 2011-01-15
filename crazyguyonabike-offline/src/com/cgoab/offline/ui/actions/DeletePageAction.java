package com.cgoab.offline.ui.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;

public class DeletePageAction extends Action {

	private TreeViewer treeViewer;

	private Shell shell;

	public DeletePageAction(Shell shell) {
		super("Delete Page");
		this.shell = shell;
	}

	public void run() {
		List<Page> pagesToDelete = new ArrayList<Page>();
		if (treeViewer.getSelection() instanceof IStructuredSelection) {
			IStructuredSelection s = (IStructuredSelection) treeViewer.getSelection();
			for (Object o : s.toList()) {
				if (o instanceof Page) {
					pagesToDelete.add((Page) o);
				}
			}
		}

		if (pagesToDelete.size() == 0) {
			return;
		}

		MessageBox confirm = new MessageBox(shell, SWT.CANCEL | SWT.OK | SWT.ICON_QUESTION);
		StringBuilder str = new StringBuilder("Are you sure you want to delete the following page(s):\n");
		for (Page p : pagesToDelete) {
			str.append("   '").append(p.getTitle()).append(":").append(p.getHeadline()).append("'\n");
		}
		confirm.setText("Confirm delete");
		confirm.setMessage(str.toString());
		if (confirm.open() == SWT.OK) {
			Object previousPageOfFirstDeletedPage = null;
			for (Page toDelete : pagesToDelete) {
				Journal journal = toDelete.getJournal();
				List<Page> pages = journal.getPages();
				int id = pages.indexOf(toDelete);
				journal.removePage(toDelete);
				if (previousPageOfFirstDeletedPage == null) {
					previousPageOfFirstDeletedPage = pages.size() == 0 ? toDelete.getJournal() : pages.get(Math.min(
							pages.size() - 1, id));
				}
			}
			treeViewer.setSelection(new StructuredSelection(previousPageOfFirstDeletedPage));
		}
	}
}
