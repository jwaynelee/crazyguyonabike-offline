package com.cgoab.offline.ui.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.ui.MainWindow;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;

public class DeletePageAction extends Action {

	private final Shell shell;

	private final TreeViewer viewer;

	public DeletePageAction(Shell shell, TreeViewer viewer) {
		super("Delete Page");
		this.shell = shell;
		this.viewer = viewer;
	}

	@Override
	public void run() {
		List<Page> pagesToDelete = new ArrayList<Page>();
		if (viewer.getSelection() instanceof IStructuredSelection) {
			IStructuredSelection s = (IStructuredSelection) viewer.getSelection();
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
		if (confirm.open() != SWT.OK) {
			return;
		}
		DeleteOperation operation = new DeleteOperation(pagesToDelete);
		operation.execute(null, null);
		OperationHistoryFactory.getOperationHistory().add(operation);
	}

	private class DeleteOperation extends AbstractOperation {

		private boolean closed;

		private JournalSelectionListener listener = new JournalSelectionListener() {

			@Override
			public void journalClosed(Journal journal) {
				closed = true;
				JournalSelectionService.getInstance().removeListener(listener);
			}

			@Override
			public void journalOpened(Journal journal) {
			}

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
			}
		};

		private List<PageAndIndex> pagesToDelete;

		public DeleteOperation(List<Page> toDelete) {
			super("delete page");
			addContext(MainWindow.APPLICATION_CONTEXT);
			this.pagesToDelete = new ArrayList<DeletePageAction.PageAndIndex>(toDelete.size());
			for (Page page : toDelete) {
				pagesToDelete.add(new PageAndIndex(page, -1));
			}
			JournalSelectionService.getInstance().addListener(listener);
		}

		@Override
		public boolean canRedo() {
			return !closed;
		}

		@Override
		public boolean canUndo() {
			return !closed;
		}

		@Override
		public IStatus execute(IProgressMonitor monitor, IAdaptable info) {
			for (PageAndIndex pi : pagesToDelete) {
				Page page = pi.page;
				List<Page> pages = page.getJournal().getPages();
				int index = pages.indexOf(page);
				pi.index = index;
				page.getJournal().removePage(page);
			}
			int a = 0;
			int b = 1 / a;
			// viewer.setSelection(new
			// StructuredSelection(previousPageOfFirstDeletedPage));
			return Status.OK_STATUS;
		}

		@Override
		public IStatus redo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
			for (PageAndIndex pi : pagesToDelete) {
				pi.page.getJournal().removePage(pi.page);
			}
			return Status.OK_STATUS;
		}

		@Override
		public IStatus undo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
			ListIterator<PageAndIndex> ipi = pagesToDelete.listIterator(pagesToDelete.size());
			while (ipi.hasPrevious()) {
				PageAndIndex pi = ipi.previous();
				pi.page.getJournal().addPage(pi.page, pi.index);
			}
			Page[] selection = new Page[pagesToDelete.size()];
			int i = 0;
			for (PageAndIndex p : pagesToDelete) {
				selection[i++] = p.page;
			}
			viewer.setSelection(new StructuredSelection(selection));
			return Status.OK_STATUS;
		}
	}

	private static class PageAndIndex {
		int index;
		Page page;

		public PageAndIndex(Page page, int index) {
			this.page = page;
			this.index = index;
		}
	}
}
