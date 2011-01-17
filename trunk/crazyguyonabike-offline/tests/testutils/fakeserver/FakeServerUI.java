package testutils.fakeserver;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import testutils.fakeserver.ServerModel.ModelListener;
import testutils.fakeserver.ServerModel.ServerJournal;
import testutils.fakeserver.ServerModel.ServerPage;
import testutils.fakeserver.ServerModel.ServerPhoto;

import com.cgoab.offline.ui.util.UIThreadCallbackMarsheller;

public class FakeServerUI {
	public void open(final FakeCGOABServer server) {
		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setLayout(new GridLayout(1, true));

		// Composite buttons = new Composite(shell, SWT.NONE);
		// buttons.setLayout(new RowLayout());
		// buttons.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true,
		// false));
		// final Button newJournal = new Button(buttons, SWT.PUSH);
		// newJournal.setText("New Journal");
		// newJournal.addSelectionListener(new SelectionAdapter() {
		// int i = 100;
		//
		// @Override
		// public void widgetSelected(SelectionEvent e) {
		// server.getModel().addJournal(new ServerJournal("Journal#" + i, i));
		// i += 1;
		// }
		// });

		TreeViewer viewer = new TreeViewer(shell);
		viewer.setContentProvider(new ServerModelProvider());
		viewer.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		viewer.setInput(server.getModel());

		shell.setSize(new Point(700, 400));
		shell.open();

		try {
			while (!shell.isDisposed()) {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			}
		} finally {
			display.dispose();
		}
	}

	public static class ServerModelProvider implements ITreeContentProvider, ModelListener {
		ServerModel model;
		Viewer viewer;

		@Override
		public void journalAdded(ServerJournal journal) {
			viewer.refresh();
		}

		public void pageAdded(ServerPage page) {
			viewer.refresh();
		}

		@Override
		public void pageRemoved(ServerPage page) {
			viewer.refresh();
		}

		public void photoAdded(ServerPhoto photo) {
			viewer.refresh();
		}

		public void userLoggedIn(String user, String id) {
			viewer.refresh();
		}

		@Override
		public void userLoggedOut(String user) {
			viewer.refresh();
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			this.viewer = viewer;
			this.model = (ServerModel) newInput;
			if (model != null) {
				model.addListener(UIThreadCallbackMarsheller.wrap(this, viewer.getControl().getShell().getDisplay()));
			}
		}

		@Override
		public void dispose() {
		}

		@Override
		public boolean hasChildren(Object element) {
			return getParent(element) != null;
		}

		@Override
		public Object getParent(Object element) {
			if (element instanceof ServerJournal) {
				return model;
			}
			if (element instanceof ServerPage) {
				return ((ServerPage) element).getJournal();
			}
			if (element instanceof ServerPhoto) {
				return ((ServerPhoto) element).getPage();
			}
			return null;
		}

		@Override
		public Object[] getElements(Object inputElement) {
			return getChildren(inputElement);
		}

		@Override
		public Object[] getChildren(Object element) {
			if (element instanceof ServerModel) {
				List<Object> i = new ArrayList<Object>();
				ServerModel m = (ServerModel) element;
				i.addAll(m.getJournals());
				i.addAll(m.getLoggedInUsers());
				return i.toArray();
			}

			if (element instanceof ServerJournal) {
				return ((ServerJournal) element).getPages().toArray();
			}
			if (element instanceof ServerPage) {
				return ((ServerPage) element).getPhotos().toArray();
			}
			return new Object[0];
		}
	}
}
