package com.cgoab.offline.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.UndoContext;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.client.UploadClientFactory;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.actions.AddPhotosAction;
import com.cgoab.offline.ui.actions.CloseJournalAction;
import com.cgoab.offline.ui.actions.DeletePageAction;
import com.cgoab.offline.ui.actions.NewJournalAction;
import com.cgoab.offline.ui.actions.NewPageAction;
import com.cgoab.offline.ui.actions.OpenJournalAction;
import com.cgoab.offline.ui.actions.OpenPageInBrowserAction;
import com.cgoab.offline.ui.actions.PurgeResizedPhotosAction;
import com.cgoab.offline.ui.actions.PurgeThumbnailCacheAction;
import com.cgoab.offline.ui.actions.RedoAction;
import com.cgoab.offline.ui.actions.SaveAction;
import com.cgoab.offline.ui.actions.ToggleHideUploadedContent;
import com.cgoab.offline.ui.actions.ToggleResizeImagesBeforeUpload;
import com.cgoab.offline.ui.actions.ToggleUseExifThumbnailAction;
import com.cgoab.offline.ui.actions.UndoAction;
import com.cgoab.offline.ui.actions.ViewResizedPhotosAction;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.util.Utils;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;

public class MainWindow extends ApplicationWindow {

	private static final Logger LOG = LoggerFactory.getLogger(MainWindow.class);

	public static final IUndoContext APPLICATION_CONTEXT = new UndoContext();

	static final String OPENJOURNALS_PREFERENCE_PATH = "/openjournals";

	AddPhotosAction addPhotosAction;

	CloseJournalAction closeJournalAction;

	private IUndoContext currentContext = APPLICATION_CONTEXT;

	DeletePageAction deletePageAction;

	MenuManager editMenuMgr;

	MenuManager fileMenuMgr;

	private JournalViewer journalView;

	private List<ContextChangedListener> listeners = new ArrayList<MainWindow.ContextChangedListener>();

	NewJournalAction newJournalAction;

	NewPageAction newPageAction;

	OpenJournalAction openJournalAction;

	OpenPageInBrowserAction openPageInBrowserAction;

	private IAction openPreferencesAction = new Action("Preferences") {
		@Override
		public void run() {
			PreferencesDialog prefs = new PreferencesDialog(getShell());
			prefs.setPreferences(preferences);
			prefs.open();
		}
	};

	PageEditor pageEditor;

	private Preferences preferences;

	PurgeResizedPhotosAction purgeResizedPhotos;

	PurgeThumbnailCacheAction purgeThumbnailCache;

	private ImageMagickResizerServiceFactory resizerServiceFactory;

	// private IAction renameJournalAction = new Action("Rename") {
	// public void run() {
	// treeViewer.editElement(((IStructuredSelection)
	// treeViewer.getSelection()).getFirstElement(), 0);
	// }
	// };

	SaveAction saveAction;

	private CachingThumbnailProviderFactory thumbnailProviderFactory;

	ThumbnailView thumbnailView;

	ToggleHideUploadedContent toggleHideUploadedContent;

	ToggleResizeImagesBeforeUpload toggleResizePhotos;

	ToggleUseExifThumbnailAction toggleUseExifThumbnailAction;

	UploadAction uploadAction;

	private UploadClientFactory uploadFactory;

	ViewResizedPhotosAction viewResizedPhotosFolder;

	public MainWindow() {
		super(null);
		Display.getCurrent().addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {
				JournalSelectionService.dispose();
			}
		});
		JournalSelectionService.init();
		setBlockOnOpen(true);
		addStatusLine();
		addMenuBar();
		setExceptionHandler(new IExceptionHandler() {
			@Override
			public void handleException(Throwable t) {
				if (t instanceof ThreadDeath) {
					throw (ThreadDeath) t;
				}

				ErrorDialog
						.openError(
								null,
								"Unhandled exception in event loop",
								"Unhandled exception in event loop. This usually indicates a bug.Please notify the application developer.",
								new Status(IStatus.ERROR, "?", t.getMessage(), t));

				LOG.warn("Unhandled exception in event loop", t);
			}
		});
	}

	public void addUndoContextChangedListener(ContextChangedListener listener) {
		listeners.add(listener);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		String name = Utils.getNameString(getClass());
		String version = Utils.getVersionString(getClass());
		name = name == null ? "?" : name;
		version = version == null ? "?" : version;
		shell.setText(name + " : " + version);
	}

	private void createActions() {
		Shell shell = getShell();
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

		/* populate main menu */
		fileMenuMgr.add(newJournalAction);
		fileMenuMgr.add(newPageAction);
		fileMenuMgr.add(openJournalAction);
		fileMenuMgr.add(new Separator());
		fileMenuMgr.add(closeJournalAction);
		fileMenuMgr.add(new Separator());
		fileMenuMgr.add(saveAction);
		fileMenuMgr.add(new Separator());
		fileMenuMgr.add(openPreferencesAction);
		fileMenuMgr.add(new Separator());
		fileMenuMgr.add(new Action("Exit") {
			@Override
			public void run() {
				getShell().close();
			}
		});

		editMenuMgr.add(new UndoAction(this));
		editMenuMgr.add(new RedoAction(this));

		/* rebuilt menu(s) */
		IContributionManager root = fileMenuMgr.getParent();
		root.update(true);
	}

	@Override
	protected Control createContents(Composite parent) {
		createControls(parent);
		createActions();

		/* TODO load on background thread? */
		String journalToOpen = preferences.getValue(OPENJOURNALS_PREFERENCE_PATH);
		if (journalToOpen != null && !JournalUtils.openJournal(journalToOpen, true, getShell())) {
			// update preferences if last journal failed to load
			preferences.removeValue(OPENJOURNALS_PREFERENCE_PATH);
			preferences.save();
		}
		return parent;
	}

	private void createControls(final Composite parent) {
		SashForm sashV = new SashForm(parent, SWT.VERTICAL);
		sashV.setLayout(new GridLayout());
		SashForm sashH = new SashForm(sashV, SWT.HORIZONTAL);
		sashH.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		journalView = new JournalViewer(this);
		journalView.createComponents(sashH);

		pageEditor = new PageEditor(this);
		pageEditor.createControls(sashH);
		Composite thumbViewerGroup = new Composite(sashV, SWT.NONE);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		thumbViewerGroup.setLayoutData(data);
		thumbViewerGroup.setLayout(new GridLayout());
		thumbnailView = new ThumbnailView(this, resizerServiceFactory, thumbnailProviderFactory);
		thumbnailView.createComponents(thumbViewerGroup);
		sashV.setWeights(new int[] { 10, 9 });
		sashH.setWeights(new int[] { 1, 3 });
	}

	@Override
	protected MenuManager createMenuManager() {
		/* actions not created yet so just make top level menus */
		MenuManager root = new MenuManager();
		fileMenuMgr = new MenuManager("File");
		editMenuMgr = new MenuManager("Edit");
		root.add(fileMenuMgr);
		root.add(editMenuMgr);
		return root;
	}

	public IUndoContext getCurrentOperationContext() {
		return currentContext;
	}

	public Preferences getPreferences() {
		return preferences;
	}

	@Override
	protected StatusLineManager getStatusLineManager() {
		return super.getStatusLineManager();
	}

	@Override
	protected void handleShellCloseEvent() {
		Journal journal = JournalSelectionService.getInstance().getCurrentJournal();
		if (journal != null) {
			preferences.setValue(OPENJOURNALS_PREFERENCE_PATH, journal.getFile().getAbsolutePath());
			preferences.save();
			if (!JournalUtils.closeJournal(journal, getShell())) {
				return; /* abort close */
			}
		}
		super.handleShellCloseEvent();
	}

	public void setCurrentUndoContext(IUndoContext newContext) {
		if (newContext == null) {
			currentContext = APPLICATION_CONTEXT;
		} else {
			currentContext = newContext;
		}
		for (ContextChangedListener listener : listeners) {
			listener.contextChanged(currentContext);
		}
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

	@Override
	protected boolean showTopSeperator() {
		return false;
	}

	public interface ContextChangedListener {
		public void contextChanged(IUndoContext context);
	}
}