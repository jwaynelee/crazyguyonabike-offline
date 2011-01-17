package com.cgoab.offline.ui;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.core.commands.operations.UndoContext;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.client.UploadClientFactory;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.ThumbnailView.StatusBarUpdater;
import com.cgoab.offline.ui.actions.AddPhotosAction;
import com.cgoab.offline.ui.actions.CloseJournalAction;
import com.cgoab.offline.ui.actions.DeletePageAction;
import com.cgoab.offline.ui.actions.NewJournalAction;
import com.cgoab.offline.ui.actions.NewPageAction;
import com.cgoab.offline.ui.actions.OpenJournalAction;
import com.cgoab.offline.ui.actions.OpenPageInBrowserAction;
import com.cgoab.offline.ui.actions.PurgeResizedPhotosAction;
import com.cgoab.offline.ui.actions.PurgeThumbnailCacheAction;
import com.cgoab.offline.ui.actions.SaveAction;
import com.cgoab.offline.ui.actions.ToggleHideUploadedContent;
import com.cgoab.offline.ui.actions.ToggleResizeImagesBeforeUpload;
import com.cgoab.offline.ui.actions.ToggleUseExifThumbnailAction;
import com.cgoab.offline.ui.actions.ViewResizedPhotosAction;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.util.Utils;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;

public class ApplicationWindow {

	private static final String OPENJOURNALS_PREFERENCE_PATH = "/openjournals";

	private JournalViewer journalView;

	// private ImageCache thumbnailCache;
	public Action newJournalAction, openJournalAction, closeJournalAction, saveAction, uploadAction, deletePageAction,
			addPhotosAction, openPageInBrowserAction, toggleHideUploadedContent, toggleResizePhotos,
			viewResizedPhotosFolder, purgeResizedPhotos, toggleUseExifThumbnailAction, purgeThumbnailCache,
			newPageAction;

	public static IUndoContext DEFAULT_CONTEXT = new UndoContext();

	IUndoContext currentContext = DEFAULT_CONTEXT;

	private IAction openPreferencesAction = new Action("Preferences") {
		@Override
		public void run() {
			PreferencesDialog prefs = new PreferencesDialog(shell);
			prefs.setPreferences(preferences);
			prefs.open();
		}
	};

	// private IAction renameJournalAction = new Action("Rename") {
	// public void run() {
	// treeViewer.editElement(((IStructuredSelection)
	// treeViewer.getSelection()).getFirstElement(), 0);
	// }
	// };

	private Preferences preferences;
	private ImageMagickResizerServiceFactory resizerServiceFactory;
	private Shell shell;
	private Label statusBar;
	private CachingThumbnailProviderFactory thumbnailProviderFactory;
	private ThumbnailView thumbnailView;

	private UploadClientFactory uploadFactory;

	public ApplicationWindow(Shell shell) {
		this.shell = shell;
		String name = Utils.getNameString(getClass());
		String version = Utils.getVersionString(getClass());
		name = name == null ? "?" : name;
		version = version == null ? "?" : version;
		shell.setText(name + " : " + version);
	}

	private void createActions(Shell shell) {
		newJournalAction = new NewJournalAction(shell);
		newPageAction = new NewPageAction();
		openJournalAction = new OpenJournalAction(shell);
		closeJournalAction = new CloseJournalAction(shell);
		saveAction = new SaveAction(shell);
		deletePageAction = new DeletePageAction(shell, journalView.getViewer());
		addPhotosAction = new AddPhotosAction(shell, thumbnailView);
		openPageInBrowserAction = new OpenPageInBrowserAction(shell);
		toggleHideUploadedContent = new ToggleHideUploadedContent();
		toggleResizePhotos = new ToggleResizeImagesBeforeUpload(thumbnailView);
		viewResizedPhotosFolder = new ViewResizedPhotosAction();
		purgeResizedPhotos = new PurgeResizedPhotosAction(shell);
		toggleUseExifThumbnailAction = new ToggleUseExifThumbnailAction();
		purgeThumbnailCache = new PurgeThumbnailCacheAction(shell);
		UploadAction uploadAction = new UploadAction(shell, thumbnailView.getViewer(), journalView.getViewer());
		uploadAction.setUploadFactory(uploadFactory);
		uploadAction.setPreferences(preferences);
		this.uploadAction = uploadAction;
	}

	private void createControls(final Shell shell) {
		GridLayout layout = new GridLayout(1, false);
		shell.setLayout(layout);
		SashForm sashV = new SashForm(shell, SWT.VERTICAL);
		SashForm sashH = new SashForm(sashV, SWT.HORIZONTAL);
		sashH.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sashV.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		journalView = new JournalViewer(this);
		journalView.createComponents(sashH);

		PageEditor pageEditor;
		pageEditor = new PageEditor(this);
		pageEditor.createControls(sashH);
		Composite thumbViewerGroup = new Composite(sashV, SWT.NONE);
		thumbViewerGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		thumbViewerGroup.setLayout(new GridLayout());
		thumbnailView = new ThumbnailView(this, resizerServiceFactory, thumbnailProviderFactory);
		thumbnailView.createComponents(thumbViewerGroup, new StatusBarUpdater() {
			@Override
			public void setStatus(String s) {
				statusBar.setText(s);
			}
		});
		sashV.setWeights(new int[] { 4, 3 });
		sashH.setWeights(new int[] { 1, 3 });

		// status & progress bar
		statusBar = new Label(shell, SWT.NONE);
		statusBar.setText("");
		statusBar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
	}

	private void createMenu(final Shell shell) {
		Menu menuBar = new Menu(shell, SWT.BAR);
		MenuItem fileMenuHeader = new MenuItem(menuBar, SWT.CASCADE);
		fileMenuHeader.setText("&File");
		Menu fileMenu = new Menu(shell, SWT.DROP_DOWN);
		fileMenuHeader.setMenu(fileMenu);

		MenuItem newJournalItem = new MenuItem(fileMenu, SWT.PUSH);
		newJournalItem.setText("&New Journal\tCtrl+Shift+N");
		newJournalItem.setAccelerator(SWT.CTRL + SWT.SHIFT + 'N');
		newJournalItem.addSelectionListener(new ActionRunner(newJournalAction));

		MenuItem newPageItem = new MenuItem(fileMenu, SWT.PUSH);
		newPageItem.setText("&New Page\tCtrl+N");
		newPageItem.setAccelerator(SWT.CTRL + 'N');
		newPageItem.addSelectionListener(new ActionRunner(newPageAction));

		new MenuItem(fileMenu, SWT.SEPARATOR);

		MenuItem fileOpenItem = new MenuItem(fileMenu, SWT.PUSH);
		fileOpenItem.setText("&Open\tCtrl+O");
		fileOpenItem.setAccelerator(SWT.CTRL + 'O');
		fileOpenItem.addSelectionListener(new ActionRunner(openJournalAction));

		new MenuItem(fileMenu, SWT.SEPARATOR);

		MenuItem fileSaveItem = new MenuItem(fileMenu, SWT.PUSH);
		fileSaveItem.setText("&Save\tCtrl+S");
		fileSaveItem.setAccelerator(SWT.CTRL + 'S');
		fileSaveItem.addSelectionListener(new ActionRunner(saveAction));

		new MenuItem(fileMenu, SWT.SEPARATOR);

		MenuItem openPreferences = new MenuItem(fileMenu, SWT.PUSH);
		openPreferences.setText("&Preferences");
		openPreferences.addSelectionListener(new ActionRunner(openPreferencesAction));

		new MenuItem(fileMenu, SWT.SEPARATOR);

		MenuItem fileExitItem = new MenuItem(fileMenu, SWT.PUSH);
		fileExitItem.setText("E&xit");
		fileExitItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});

		MenuItem editMenuHeader = new MenuItem(menuBar, SWT.CASCADE);
		editMenuHeader.setText("&Edit");
		Menu editMenu = new Menu(shell, SWT.DROP_DOWN);
		editMenuHeader.setMenu(editMenu);

		final MenuItem undo = new MenuItem(editMenu, SWT.DROP_DOWN);
		undo.setText("Undo\tCtrl+Z");
		undo.setAccelerator(SWT.CTRL + 'Z');
		undo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					OperationHistoryFactory.getOperationHistory().undo(currentContext, null, null);
				} catch (ExecutionException e1) {
					e1.printStackTrace();
				}
			}
		});

		final MenuItem redo = new MenuItem(editMenu, SWT.DROP_DOWN);
		redo.setText("Redo\tCtrl+Y");
		redo.setAccelerator(SWT.CTRL + 'Y');
		redo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					OperationHistoryFactory.getOperationHistory().redo(currentContext, null, null);
				} catch (ExecutionException e1) {
					e1.printStackTrace();
				}
			}
		});

		editMenu.addMenuListener(new MenuListener() {

			@Override
			public void menuHidden(MenuEvent e) {
			}

			@Override
			public void menuShown(MenuEvent e) {
				IOperationHistory history = OperationHistoryFactory.getOperationHistory();
				if (history.canUndo(currentContext)) {
					undo.setEnabled(true);
					undo.setText("Undo " + history.getUndoOperation(currentContext).getLabel() + " \tCtrl+Z");
				} else {
					undo.setEnabled(false);
					undo.setText("Undo\tCtrl+Z");
				}
				if (history.canRedo(currentContext)) {
					redo.setEnabled(true);
					redo.setText("Redo " + history.getRedoOperation(currentContext).getLabel() + " \tCtrl+Y");
				} else {
					redo.setEnabled(false);
					redo.setText("Redo\tCtrl+Y");
				}
			}
		});

		shell.setMenuBar(menuBar);
	}

	private boolean onClose() {
		Journal journal = JournalSelectionService.getInstance().getCurrentJournal();
		if (journal != null) {
			preferences.setValue(OPENJOURNALS_PREFERENCE_PATH, journal.getFile().getAbsolutePath());
			preferences.save();
			if (!JournalUtils.closeJournal(journal, shell)) {
				return false;
			}
		}
		return true;
	}

	public void open() {
		registerCloseListener(shell);
		createControls(shell);
		createActions(shell);
		createMenu(shell);

		shell.pack();
		shell.open();

		/* TODO load on background thread? */
		String journalToOpen = preferences.getValue(OPENJOURNALS_PREFERENCE_PATH);
		if (journalToOpen != null && !JournalUtils.openJournal(journalToOpen, true, shell)) {
			// update preferences if last journal failed to load
			preferences.removeValue(OPENJOURNALS_PREFERENCE_PATH);
			preferences.save();
		}

		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch()) {
				shell.getDisplay().sleep();
			}
		}
	}

	private void registerCloseListener(Shell shell) {
		shell.addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (!onClose()) {
					event.doit = false;
				}
			}
		});
	}

	public void setPreferences(Preferences preferences) {
		this.preferences = preferences;
	}

	public void setResizerServiceFactory(ImageMagickResizerServiceFactory resizerServiceFactory) {
		this.resizerServiceFactory = resizerServiceFactory;
	}

	public void setThumbnailProviderFactory(CachingThumbnailProviderFactory thumbnailFactory) {
		thumbnailProviderFactory = thumbnailFactory;
	}

	public void setUploadFactory(UploadClientFactory uploadFactory) {
		this.uploadFactory = uploadFactory;
	}

	public void setCurrentUndoContext(IUndoContext newContext) {
		if (newContext == null) {
			currentContext = DEFAULT_CONTEXT;
		} else {
			currentContext = newContext;
		}
		System.out.println("NewContext => " + currentContext);
	}
}