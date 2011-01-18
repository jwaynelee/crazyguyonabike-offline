package com.cgoab.offline.ui;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;

import com.cgoab.offline.client.BatchUploader;
import com.cgoab.offline.client.BatchUploader.BatchUploaderListener;
import com.cgoab.offline.client.CompletionCallback;
import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.DocumentType;
import com.cgoab.offline.client.UploadClient;
import com.cgoab.offline.client.mock.MockClient;
import com.cgoab.offline.client.web.AbstractFormBinder.InitializationErrorException;
import com.cgoab.offline.client.web.AbstractFormBinder.InitializationException;
import com.cgoab.offline.client.web.AbstractFormBinder.InitializationWarningException;
import com.cgoab.offline.client.web.HtmlProvider;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.ui.util.ErrorBoxWithHtml;
import com.cgoab.offline.util.Utils;

public class UploadDialog {

	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		MockClient client = new MockClient();
		UploadDialog uploadDialog = new UploadDialog(shell);
		uploadDialog.setUploadClient(client);
		Journal journal = new Journal(null, "test");
		journal.createNewPage();
		journal.createNewPage();
		journal.createNewPage();
		uploadDialog.setJournal(journal);
		uploadDialog.setPages(journal.getPages());
		uploadDialog.open();
		display.dispose();
		client.dispose();
	}

	private Button btnCancel;

	private Button btnLogin;

	private Button btnLogout;

	private Button btnUpload;

	private final SelectionListener cancelCurrentOperationListener = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent e) {
			System.err.println("CANCEL OP");
			client.cancel();
		}
	};

	private UploadClient client;

	private SelectionListener closeShellListener = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent e) {
			System.err.println("CLOSE SHELL");
			shell.close();
		}
	};

	private TableViewer documentTable;

	private Journal journal;

	private ProgressBar overallProgressBar;

	private List<Page> pages;

	private Label photoPreview;

	private ProgressBar photoProgressBar;

	private UploadResult result;

	private Shell shell;

	private ThumbnailProvider thumbnailProvider;

	private Text txtUsername, txtPassword;

	private Text uploadLog;

	public UploadDialog(Shell parent) {
		shell = new Shell(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM);

		/* stop shell closing on 'esc' */
		shell.addListener(SWT.Traverse, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (event.detail == SWT.TRAVERSE_ESCAPE) {
					event.doit = false;
				}
			}
		});

		/* dispose last uploaded image on close */
		shell.addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event e) {
				Image img = photoPreview.getImage();
				if (img != null) {
					img.dispose();
				}
			}
		});
	}

	private void createDocumentViewer(Shell shell) {
		documentTable = new TableViewer(shell, SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
		layoutData.horizontalSpan = 2;
		layoutData.minimumHeight = 80;
		documentTable.getTable().setLayoutData(layoutData);
		documentTable.setContentProvider(new IStructuredContentProvider() {
			@Override
			public void dispose() {
			}

			@Override
			public Object[] getElements(Object inputElement) {
				if (inputElement == null) {
					return null;
				}
				return ((List<?>) inputElement).toArray();
			}

			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}
		});

		// columns
		TableViewerColumn colTitle = new TableViewerColumn(documentTable, SWT.NONE);
		colTitle.getColumn().setText("Title");
		colTitle.getColumn().setResizable(true);
		colTitle.getColumn().setWidth(150);
		colTitle.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((DocumentDescription) element).getTitle();
			}
		});

		TableViewerColumn colStatus = new TableViewerColumn(documentTable, SWT.NONE);
		colStatus.getColumn().setText("Status");
		colStatus.getColumn().setResizable(true);
		colStatus.getColumn().setWidth(150);
		colStatus.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((DocumentDescription) element).getStatus();
			}
		});

		TableViewerColumn colId = new TableViewerColumn(documentTable, SWT.NONE);
		colId.getColumn().setText("ID");
		colId.getColumn().setResizable(true);
		colId.getColumn().setWidth(60);
		colId.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return Integer.toString(((DocumentDescription) element).getDocumentId());
			}
		});

		/* remove non-journals */
		documentTable.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				return ((DocumentDescription) element).getType() == DocumentType.JOURNAL;
			}
		});
		documentTable.getTable().setLinesVisible(true);
		documentTable.getTable().setHeaderVisible(true);
		documentTable.setColumnProperties(new String[] { "name", "status", "id" });
		documentTable.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				if (((IStructuredSelection) documentTable.getSelection()).getFirstElement() != null) {
					btnUpload.setEnabled(true);
				} else {
					btnUpload.setEnabled(false);
				}
			}
		});
	}

	private void doLogin(final String username, String password) {
		// disable login button and fields
		setEnabledTo(false, txtUsername, txtPassword, btnLogin, btnLogout);

		if (username == null) {
			log("Checking if already logged in...");
		} else {
			log("Logging in as \"" + username + "\"...");
		}

		startOperation();
		client.login(username, password, new CompletionCallback<String>() {
			@Override
			public void onCompletion(final String actualUsername) {
				finishOperation();
				log("Logged in as " + actualUsername + " (" + client.getCurrentUserRealName() + ")");
				if (username == null) {
					txtUsername.setText(actualUsername);
				}
				setEnabledTo(true, btnLogout);
				refreshDocumentList(journal.getDocIdHint());
			}

			@Override
			public void onError(final Throwable t) {
				finishOperation();
				log("Failed to login");
				setEnabledTo(true, txtUsername, txtPassword, btnLogin);
				if (username != null) {
					// don't prompt if we just did an auto-login
					showError("logging in", "Failed to login to the server", t);
				}
			}

			@Override
			public void retryNotify(Throwable exception, int retryCount) {
				/* ignore */
			}
		});
	}

	private void doLogout() {
		setEnabledTo(false, btnLogout);
		log("Logging out...");
		startOperation();
		client.logout(new CompletionCallback<Void>() {
			@Override
			public void onCompletion(Void result) {
				finishOperation();
				restoreUI();
			}

			@Override
			public void onError(Throwable exception) {
				// TODO handle error?
				finishOperation();
				restoreUI();
			}

			private void restoreUI() {
				documentTable.setInput(null);
				setEnabledTo(true, txtUsername, txtPassword, btnLogin);
				setEnabledTo(false, btnLogout, btnUpload, documentTable.getTable());
				txtUsername.setFocus();
			}

			@Override
			public void retryNotify(Throwable exception, int retryCount) {
			}
		});
	}

	private void doUpload() {
		// disable UI
		setEnabledTo(false, btnUpload, btnLogout, documentTable.getTable());
		final DocumentDescription selectedDocument = (DocumentDescription) ((IStructuredSelection) documentTable
				.getSelection()).getFirstElement();
		selectedDocument.getDocumentId();

		// save doc id hint in journal for future use
		journal.setDocIdHint(selectedDocument.getDocumentId());

		// compute how much work we have to do...
		int work = 0;
		for (Page page : pages) {
			work++;
			for (Photo p : page.getPhotos()) {
				if (p.getState() == UploadState.NEW || p.getState() == UploadState.ERROR) {
					work++;
				}
			}
		}
		overallProgressBar.setMinimum(0);
		overallProgressBar.setMaximum(work);
		photoProgressBar.setMinimum(0);
		photoProgressBar.setMaximum(100);

		final BatchUploader uploader = new BatchUploader(client);
		uploader.setPages(pages);
		uploader.setListener(new UploadListener());

		/* first initialise the client */
		log("Checking for server changes using document-id " + selectedDocument.getDocumentId());
		startOperation();
		client.initialize(selectedDocument.getDocumentId(), new CompletionCallback<Void>() {
			@Override
			public void onCompletion(Void result) {
				finishOperation();
				log("Initialization complete");
				startUpload();
			}

			public void onError(Throwable exception) {
				finishOperation();
				if (exception instanceof InitializationException) {
					if (showInitializationException((InitializationException) exception)) {
						startUpload();
						return;
					}
				} else {
					showError("Initialization", "Error during initialization", exception);
				}
			}

			@Override
			public void retryNotify(Throwable exception, int retryCount) {
				/* ignore */
			}

			private void startUpload() {
				startOperation();
				log("Starting upload of " + pages.size() + " pages to " + selectedDocument.getDocumentId() + " ("
						+ selectedDocument.getTitle() + ")");
				uploader.start();
			}
		});
	}

	/*
	 * Allow user to close shell again
	 */
	private void finishOperation() {
		btnCancel.addSelectionListener(closeShellListener);
		btnCancel.removeSelectionListener(cancelCurrentOperationListener);
	}

	private Image getThumbnail(Photo photo) {
		Future<Thumbnail> result = thumbnailProvider.get(photo.getFile(), null, null);
		try {
			/* don't wait too long otherwise upload will slow down */
			return new Image(shell.getDisplay(), result.get(500, TimeUnit.MILLISECONDS).imageData);
		} catch (Exception e) {
			/* ignore, just trying to be helpful */
			return null;
		}
	}

	private void log(final String str) {
		if (Thread.currentThread() != shell.getDisplay().getThread()) {
			shell.getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					log(str);
				}
			});
		} else {
			String time = DateTimeFormat.mediumTime().print(new LocalDateTime());
			uploadLog.append(time + " - " + str + Text.DELIMITER);
		}
	}

	public UploadResult open() {
		shell.setText("Upload journal to (" + client + ")");
		GridLayout layout = new GridLayout();
		layout.marginTop = layout.marginBottom = 4;
		layout.marginLeft = layout.marginRight = 4;
		shell.setLayout(layout);
		GridData data;

		// message
		Label message = new Label(shell, SWT.NONE);
		message.setText("Login and select the document to add these "
				+ pages.size()
				+ " page(s) to."
				+ (client instanceof MockClient ? "\n\nWARNING USING MOCK CLIENT, NOTHING WILL BE SENT TO SERVER!!!"
						: ""));
		data = new GridData(SWT.FILL, SWT.TOP, true, false);
		message.setLayoutData(data);

		data = new GridData();
		data.heightHint = 15;
		Label spacer = new Label(shell, SWT.NONE);
		spacer.setLayoutData(data);

		Label line = new Label(shell, SWT.SEPARATOR | SWT.SHADOW_OUT | SWT.HORIZONTAL);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		line.setLayoutData(data);

		// login group
		Composite loginGroup = new Composite(shell, SWT.NONE);
		loginGroup.setLayout(new GridLayout(5, false));
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		loginGroup.setLayoutData(data);

		new Label(loginGroup, SWT.NONE).setText("Username:");
		txtUsername = new Text(loginGroup, SWT.SINGLE | SWT.BORDER);
		txtUsername.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		new Label(loginGroup, SWT.NONE).setText("Password:");
		txtPassword = new Text(loginGroup, SWT.SINGLE | SWT.PASSWORD | SWT.BORDER);
		txtPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Composite loginButtons = new Composite(loginGroup, SWT.NONE);
		loginButtons.setLayout(new RowLayout());
		loginButtons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		btnLogout = new Button(loginButtons, SWT.PUSH);
		btnLogout.setText("Logout");
		btnLogout.setEnabled(false);
		btnLogout.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doLogout();
			}
		});
		btnLogin = new Button(loginButtons, SWT.PUSH);
		btnLogin.setText("Login");
		btnLogin.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doLogin(txtUsername.getText(), txtPassword.getText());
			}
		});

		// line
		line = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		line.setLayoutData(data);

		new Label(shell, SWT.NONE).setText("Select a document");
		// tableText.setLayoutData( new GridData(SWT.FILL, SWT.CENTER, true,
		// false));

		createDocumentViewer(shell);

		// disable initially as we're not logged in
		setEnabledTo(false, documentTable.getTable());

		Label line2 = new Label(shell, SWT.SEPARATOR | SWT.SHADOW_OUT | SWT.HORIZONTAL);
		new Label(shell, SWT.NONE).setText("Upload progress");
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		line2.setLayoutData(data);

		Composite uploadStatusGroup = new Composite(shell, SWT.NONE);
		layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		uploadStatusGroup.setLayout(layout);
		data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.minimumHeight = 100;
		uploadStatusGroup.setLayoutData(data);

		// log
		uploadLog = new Text(uploadStatusGroup, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY);
		data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.heightHint = uploadLog.getLineHeight() * 5;
		GC tempGC = new GC(uploadLog);
		int averageCharWidth = tempGC.getFontMetrics().getAverageCharWidth();
		tempGC.dispose();
		data.widthHint = averageCharWidth * 80;
		uploadLog.setLayoutData(data);

		photoPreview = new Label(uploadStatusGroup, SWT.BORDER | SWT.CENTER);
		data = new GridData(SWT.RIGHT, SWT.TOP, false, false);
		data.widthHint = ThumbnailViewer.THUMBNAIL_WIDTH;
		data.heightHint = ThumbnailViewer.THUMBNAIL_HEIGHT;
		photoPreview.setLayoutData(data);

		// progress
		// Composite progressGroup = new Composite(uploadStatusGroup, SWT.NONE);
		// GridLayout layout2 = new GridLayout(4, false);
		// layout2.marginWidth = 0;
		// progressGroup.setLayout(layout2);
		// progressGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
		// false));
		// new Label(progressGroup, SWT.NONE).setText("Photo");
		overallProgressBar = new ProgressBar(uploadStatusGroup, SWT.NONE);
		overallProgressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		photoProgressBar = new ProgressBar(uploadStatusGroup, SWT.NONE);
		photoProgressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		// Label overall = new Label(progressGroup, SWT.NONE);
		// overall.setText("Overall");
		// overall.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false,
		// false));

		// cancel & upload buttons
		Composite buttons = new Composite(shell, SWT.NONE);
		buttons.setLayout(new GridLayout(2, true));
		data = new GridData(SWT.CENTER, SWT.BOTTOM, false, false);
		data.horizontalSpan = 2;
		buttons.setLayoutData(data);

		btnCancel = new Button(buttons, SWT.PUSH);
		data = new GridData(SWT.RIGHT, SWT.CENTER, true, false);
		data.minimumWidth = 100;
		btnCancel.setLayoutData(data);
		btnCancel.setText("Cancel");
		btnCancel.addSelectionListener(closeShellListener);

		btnUpload = new Button(buttons, SWT.PUSH);
		data = new GridData(SWT.RIGHT, SWT.CENTER, true, false);
		data.minimumWidth = 100;
		btnUpload.setLayoutData(data);
		btnUpload.setText("Upload");
		btnUpload.setEnabled(false);
		btnUpload.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doUpload();
			}
		});
		shell.setDefaultButton(btnLogin);
		shell.pack();
		shell.open();

		// attempt auto-login (using cookie)
		doLogin(null, null);

		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch()) {
				shell.getDisplay().sleep();
			}
		}

		return result;
	}

	private void refreshDocumentList(final int defaultSelection) {
		startOperation();
		client.getDocuments(new CompletionCallback<List<DocumentDescription>>() {
			@Override
			public void onCompletion(final List<DocumentDescription> result) {
				finishOperation();
				log("Found " + result.size() + " documents on the server");
				documentTable.getTable().setEnabled(true);
				documentTable.setInput(result);

				// default selection if possible
				if (defaultSelection != Journal.UNSET_DOC_ID) {
					for (DocumentDescription doc : result) {
						if (doc.getDocumentId() == defaultSelection) {
							documentTable.setSelection(new StructuredSelection(doc), true);
							break;
						}
					}
				}
			}

			@Override
			public void onError(Throwable exception) {
				finishOperation();
				log("Failed to get documents [" + exception.getMessage() + "]");
				showError("get documents", "Failed to get your document list from the server", exception);
			}

			@Override
			public void retryNotify(Throwable exception, int retryCount) {
				// don't care about retires
			}
		});
	}

	private void setEnabledTo(boolean value, Control... widgets) {
		for (Control widget : widgets) {
			widget.setEnabled(value);
		}
	}

	public void setJournal(Journal journal) {
		this.journal = journal;
	}

	public void setPages(List<Page> pages) {
		this.pages = pages;
	}

	public void setThumbnailProvider(ThumbnailProvider thumbnailProvider) {
		this.thumbnailProvider = thumbnailProvider;
	}

	public void setUploadClient(UploadClient client) {
		this.client = client;
	}

	private void showError(final String action, final String message, final Throwable t) {
		if (t instanceof HtmlProvider) {
			ErrorBoxWithHtml p = new ErrorBoxWithHtml(shell);
			p.setHtml(((HtmlProvider) t).getHtml());
			p.setTitle("Error " + action);
			p.setMessage(message + "\n\nException: " + t.toString());
			p.open();
		} else { /* all other exceptions */
			MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
			box.setText("Error " + action);
			box.setMessage(message + "\n\nException: " + t.toString());
			box.open();
		}
	}

	private boolean showInitializationException(InitializationException t) {
		if (t instanceof InitializationErrorException) {
			log("Detected fatal server changes");
			MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
			box.setText("Detected fatal server changes");
			box.setMessage("Potentially fatal server changes detected (details below).\n\nThese changes "
					+ "indicate the server has changed since this software was released. "
					+ "Check for updates and contact the author if you are already using the latest verersion.\n\n"
					+ t.getMessage());
			box.open();
		} else if (t instanceof InitializationWarningException) {
			log("Detected server changes");
			MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.YES | SWT.NO);
			box.setText("Detected server changes");
			box.setMessage("Server changes detected (details below). Do you want to proceed with the upload?\n\nThese changes "
					+ "indicate the server has changed since this software was released, however no fatal changes "
					+ "were detected so it may be safe to continue. Check for updates and contact the author "
					+ "if you are already using the latest verersion.\n\n" + t.getMessage());
			if (box.open() == SWT.YES) {
				/* continue */
				return true;
			}
		} else {
			throw new IllegalArgumentException("Unexpected exception type: " + t);
		}
		return false;
	}

	private void startOperation() {
		btnCancel.addSelectionListener(cancelCurrentOperationListener);
		btnCancel.removeSelectionListener(closeShellListener);
	}

	private class UploadListener implements BatchUploaderListener {

		@Override
		public void afterUploadPage(Page page) {
			overallProgressBar.setSelection(overallProgressBar.getSelection() + 1);
		}

		@Override
		public void afterUploadPhoto(Photo photo) {
			overallProgressBar.setSelection(overallProgressBar.getSelection() + 1);
		}

		@Override
		public void beforeUploadPage(Page page) {
			overallProgressBar.setState(SWT.NORMAL);
			photoProgressBar.setState(SWT.NORMAL);
			photoProgressBar.setSelection(0);
			log("Uploading page [" + page.getTitle() + "]");
		}

		@Override
		public void beforeUploadPhoto(Photo photo) {
			Image previousPhoto = photoPreview.getImage();
			if (previousPhoto != null) {
				previousPhoto.dispose();
			}

			Image thumbnail = getThumbnail(photo);
			if (thumbnail != null) {
				photoPreview.setImage(thumbnail);
			}

			overallProgressBar.setState(SWT.NORMAL);
			photoProgressBar.setState(SWT.NORMAL);
			photoProgressBar.setSelection(0);

			/* report actual uploaded photo size */
			File file = photo.getResizedPhoto();
			boolean resized;
			if (file == null) {
				file = photo.getFile();
				resized = false;
			} else {
				resized = true;
			}

			log("Uploading photo " + file.getName() + " (" + (resized ? "resized to " : "")
					+ Utils.formatBytes(file.length()) + ")");
		}

		@Override
		public void finished(List<Page> uploaded) {
			finishOperation();
			log("Upload complete (" + uploaded.size() + " pages uploaded)");
			MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
			box.setText("Upload completed");
			box.setMessage("The upload completed sucessfully. Press OK to return");
			box.open();
			result = new UploadResult(true, null, null);
			shell.close();
		}

		@Override
		public void finishedWithError(List<Page> pagesThatUploaded, List<Page> pagesNotUploaded, Page currentPage,
				Photo currentPhoto, Throwable error) {
			finishOperation();
			log("Upload failed on page " + currentPage.getTitle());
			overallProgressBar.setState(SWT.ERROR);
			photoProgressBar.setState(SWT.ERROR);
			result = new UploadResult(false, currentPage, currentPhoto);

			// TODO we could offer "retry" here if the error looks transient and
			// was for some reason not retried automatically?

			showError("uploading", "Upload failed. Some pages may not be fully uploaded. Fix the error and retry",
					error);

			// leave the shell open, allow user to read
			// the log and then close via cancel
		}

		@Override
		public void retryPage(Page page, Throwable error, int retryCount) {
			overallProgressBar.setState(SWT.ERROR);
			log("Retrying page upload due to: " + error.toString());
		}

		@Override
		public void retryPhoto(Photo photo, Throwable error, int retryCount) {
			photoProgressBar.setState(SWT.ERROR);
			log("Retrying photo upload due to: " + error.toString());
		}

		@Override
		public void uploadPhotoProgress(Photo photo, long bytes, long total) {
			int percent = (int) ((100 * bytes) / total);
			photoProgressBar.setSelection(percent);
		}
	}

	public class UploadResult {
		boolean complete;
		Page lastPage;
		Photo lastPhoto;

		public UploadResult(boolean complete, Page lastPage, Photo lastPhoto) {
			this.complete = complete;
			this.lastPage = lastPage;
			this.lastPhoto = lastPhoto;
		}

		/**
		 * Returns the page that was been uploaded when the error occurred (only
		 * valid when {@link #isComplete()} returns <tt>false</tt>)
		 * 
		 * @return
		 */
		public Page getLastPage() {
			return lastPage;
		}

		/**
		 * Returns the photo that was been uploaded when the error occurred (may
		 * be null if it was a page and not a photo that failed to upload).
		 * 
		 * @return
		 */
		public Photo getLastPhoto() {
			return lastPhoto;
		}

		/**
		 * Returns true if the upload fully completed
		 * 
		 * @return
		 */
		public boolean isComplete() {
			return complete;
		}
	}
}
