package com.cgoab.offline.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
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
import com.cgoab.offline.client.UploadClient;
import com.cgoab.offline.client.mock.MockClient;
import com.cgoab.offline.client.web.HtmlProvider;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.ui.util.ErrorBoxWithHtml;
import com.cgoab.offline.ui.util.UIThreadCallbackMarsheller;
import com.cgoab.offline.util.Utils;
import com.cgoab.offline.util.resizer.ResizerService;

public class UploadDialog {

	private static final String NOT_LOGGED_IN_TXT = "Not logged in";

	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL);
		MockClient client = new MockClient();
		UploadDialog uploadDialog = new UploadDialog(shell);
		uploadDialog.setUploadClient(client);
		uploadDialog.setPages(new ArrayList<Page>());
		uploadDialog.setJournal(new Journal(null, "bar"));
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
			client.cancel();
		}
	};

	private UploadClient client;

	private SelectionListener closeShellListener;

	private TableViewer documentTable;

	private Journal journal;

	private ProgressBar overallProgressBar;

	private List<Page> pages;

	private Label photoPreview;

	private ProgressBar photoProgressBar;

	private UploadResult result;

	private Shell shell;

	private Label statusLine;

	private Text txtUsername, txtPassword;

	private Text uploadStatus;

	public UploadDialog(Shell parent) {
		shell = new Shell(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM);
		shell.addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event e) {
				// dispose of the final image to be uploaded
				Image img = photoPreview.getImage();
				if (img != null) {
					img.dispose();
				}
			}
		});
	}

	private void asyncExec(Runnable work) {
		shell.getDisplay().asyncExec(work);
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
				return ((List<DocumentDescription>) inputElement).toArray();
			}

			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}
		});

		// columns
		TableViewerColumn colTitle = new TableViewerColumn(documentTable, SWT.NONE);
		colTitle.getColumn().setText("Title");
		colTitle.getColumn().setResizable(true);
		colTitle.getColumn().setWidth(200);
		colTitle.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((DocumentDescription) element).getTitle();
			}
		});
		TableViewerColumn colId = new TableViewerColumn(documentTable, SWT.NONE);
		colId.getColumn().setText("ID");
		colId.getColumn().setResizable(true);
		colId.getColumn().setWidth(150);
		colId.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return Integer.toString(((DocumentDescription) element).getDocumentId());
			}
		});

		documentTable.getTable().setLinesVisible(true);
		documentTable.getTable().setHeaderVisible(true);
		documentTable.setColumnProperties(new String[] { "name", "id" });
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
			statusLine.setText("Checking if already logged in...");
		} else {
			statusLine.setText("Logging in as \"" + username + "\"...");
		}

		client.login(username, password, new CompletionCallback<String>() {
			@Override
			public void onCompletion(final String actualUsername) {
				asyncExec(new Runnable() {
					@Override
					public void run() {
						log("Logged in as " + actualUsername);
						statusLine.setText("Logged in as \"" + actualUsername + "\" ("
								+ client.getCurrentUserRealName() + ")");
						if (username == null) {
							txtUsername.setText(actualUsername);
						}
						setEnabledTo(true, btnLogout);
					}
				});
				refreshDocumentList(journal.getDocIdHint());
			}

			@Override
			public void onError(final Throwable t) {
				asyncExec(new Runnable() {
					@Override
					public void run() {
						statusLine.setText(NOT_LOGGED_IN_TXT);
						setEnabledTo(true, txtUsername, txtPassword, btnLogin);
						if (username != null) {
							// don't prompt if we just did an auto-login
							showError("logging in", "Failed to login to the server", t);
						}
					}
				});
			}

			@Override
			public void retryNotify(Throwable exception, int retryCount) {
				// don't care about retries
			}
		});
	}

	private void doLogout() {
		setEnabledTo(false, btnLogout);
		statusLine.setText("Logging out...");
		client.logout(new CompletionCallback<Void>() {
			@Override
			public void onCompletion(Void result) {
				asyncExec(new Runnable() {
					@Override
					public void run() {
						restoreUI();
					}
				});
			}

			@Override
			public void onError(Throwable exception) {
				// TODO handle error?
				asyncExec(new Runnable() {
					@Override
					public void run() {
						restoreUI();
					}
				});
			}

			private void restoreUI() {
				documentTable.setInput(null);
				setEnabledTo(true, txtUsername, txtPassword, btnLogin);
				setEnabledTo(false, btnLogout, btnUpload, documentTable.getTable());
				txtUsername.setFocus();
				statusLine.setText(NOT_LOGGED_IN_TXT);
			}

			@Override
			public void retryNotify(Throwable exception, int retryCount) {
			}
		});
	}

	/**
	 * Make sure all photos are available
	 * 
	 * @throws IOException
	 */
	private void prepareForUpload() {
		// TODO make sure every photo exists...
	}

	private ThumbnailProvider thumbnailProvider;

	private ResizerService resizerService;

	private void doUpload() {
		// disable UI
		setEnabledTo(false, btnUpload, btnLogout, documentTable.getTable());

		prepareForUpload();

		// remove the cancellation action and instead cancel this operation
		btnCancel.removeSelectionListener(closeShellListener);
		btnCancel.addSelectionListener(cancelCurrentOperationListener);

		final int documentID = ((DocumentDescription) ((IStructuredSelection) documentTable.getSelection())
				.getFirstElement()).getDocumentId();

		// save doc id hint in journal for future use
		journal.setDocIdHint(documentID);

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

		BatchUploader uploader = new BatchUploader(client);
		uploader.setDocumentId(documentID);
		uploader.setPages(pages);
		uploader.setListener(UIThreadCallbackMarsheller.wrap(new UploadListener(), shell.getDisplay()));
		log("Starting upload of " + pages.size() + " pages to " + documentID);
		uploader.start();
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
			uploadStatus.append(time + " - " + str + Text.DELIMITER);
		}
	}

	public UploadResult open() {
		shell.setText("Upload to journal to (" + client + ")");
		GridLayout layout = new GridLayout(2, false);
		layout.marginTop = layout.marginBottom = 5;
		layout.marginLeft = layout.marginRight = 5;
		shell.setLayout(layout);
		GridData data;

		// message
		Label message = new Label(shell, SWT.NONE);
		message.setText("Login and select the document to add these "
				+ pages.size()
				+ " page(s)."
				+ (client instanceof MockClient ? "\n\nWARNING USING MOCK CLIENT, NOTHING WILL BE SENT TO SERVER!!!"
						: ""));
		data = new GridData(SWT.FILL, SWT.TOP, true, false);
		data.horizontalSpan = 2;
		message.setLayoutData(data);

		data = new GridData();
		data.heightHint = 15;
		data.horizontalSpan = 2;
		Label spacer = new Label(shell, SWT.NONE);
		spacer.setLayoutData(data);

		Label line = new Label(shell, SWT.SEPARATOR | SWT.SHADOW_OUT | SWT.HORIZONTAL);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		line.setLayoutData(data);

		// login group
		Composite loginGroup = new Composite(shell, SWT.NONE);
		loginGroup.setLayout(new GridLayout(2, false));
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		loginGroup.setLayoutData(data);

		new Label(loginGroup, SWT.NONE).setText("Username:");
		txtUsername = new Text(loginGroup, SWT.SINGLE | SWT.BORDER);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		txtUsername.setLayoutData(data);

		new Label(loginGroup, SWT.NONE).setText("Password:");
		txtPassword = new Text(loginGroup, SWT.SINGLE | SWT.PASSWORD | SWT.BORDER);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		txtPassword.setLayoutData(data);

		Composite loginButtons = new Composite(loginGroup, SWT.NONE);
		loginButtons.setLayout(new RowLayout());
		data = new GridData(SWT.RIGHT, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		loginButtons.setLayoutData(data);
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

		statusLine = new Label(shell, SWT.NONE);
		statusLine.setText(NOT_LOGGED_IN_TXT);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		statusLine.setLayoutData(data);

		// line
		line = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		line.setLayoutData(data);

		Label tableText = new Label(shell, SWT.NONE);
		tableText.setText("Select a document");
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		tableText.setLayoutData(data);

		createDocumentViewer(shell);

		// disable initially as we're not logged in
		setEnabledTo(false, documentTable.getTable());

		Label line2 = new Label(shell, SWT.SEPARATOR | SWT.SHADOW_OUT | SWT.HORIZONTAL);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		line2.setLayoutData(data);

		Composite uploadStatusGroup = new Composite(shell, SWT.NONE);
		uploadStatusGroup.setLayout(new GridLayout(2, false));
		data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.minimumHeight = 100;
		data.horizontalSpan = 2;
		uploadStatusGroup.setLayoutData(data);

		// log
		uploadStatus = new Text(uploadStatusGroup, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY);
		data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.heightHint = uploadStatus.getLineHeight() * 5;
		GC tempGC = new GC(uploadStatus);
		int averageCharWidth = tempGC.getFontMetrics().getAverageCharWidth();
		tempGC.dispose();
		data.widthHint = averageCharWidth * 80;
		uploadStatus.setLayoutData(data);

		photoPreview = new Label(uploadStatusGroup, SWT.BORDER | SWT.CENTER);
		data = new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 3);
		data.widthHint = ThumbnailViewer.THUMBNAIL_WIDTH;
		data.heightHint = ThumbnailViewer.THUMBNAIL_HEIGHT;
		photoPreview.setLayoutData(data);

		// progress
		photoProgressBar = new ProgressBar(uploadStatusGroup, SWT.NONE);
		photoProgressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		overallProgressBar = new ProgressBar(uploadStatusGroup, SWT.NONE);
		overallProgressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

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
		closeShellListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		};
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
		client.getDocuments(new CompletionCallback<List<DocumentDescription>>() {
			@Override
			public void onCompletion(final List<DocumentDescription> result) {
				asyncExec(new Runnable() {
					@Override
					public void run() {
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

				});
			}

			@Override
			public void onError(Throwable exception) {
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

	private void showError(final String action, final String message, final Throwable t) {
		if (shell.getDisplay().getThread() != Thread.currentThread()) {
			asyncExec(new Runnable() {
				@Override
				public void run() {
					// move onto UI thread
					showError(action, message, t);
				}
			});
		} else {
			if (t instanceof HtmlProvider) {
				ErrorBoxWithHtml p = new ErrorBoxWithHtml(shell);
				p.setHtml(((HtmlProvider) t).getHtml());
				p.setTitle("Error " + action);
				p.setMessage(message + "\n\nException: " + t.toString());
				p.open();
			} else {
				MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
				box.setText("Error " + action);
				box.setMessage(message + "\n\nException: " + t.toString());
				box.open();
			}
		}
	}

	// TODO load the next days photos whilst uploading previous day???
	private Image getOrLoadThumbnail(Photo photo) {

		Future<Thumbnail> result = thumbnailProvider.get(photo.getFile(), null, null);

		// block until it loads
		try {
			return new Image(shell.getDisplay(), result.get().imageData);
		} catch (Exception e) {
			return null; // ignore errors loading thumb, we are just trying
							// to be helpful...
		}
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
			log("Uploading page [" + page.getTitle() + "]");
		}

		@Override
		public void beforeUploadPhoto(Photo photo) {
			Image previousPhoto = photoPreview.getImage();
			if (previousPhoto != null) {
				previousPhoto.dispose();
			}

			Image thumbnail = getOrLoadThumbnail(photo);
			if (thumbnail != null) {
				photoPreview.setImage(thumbnail);
			}

			overallProgressBar.setState(SWT.NORMAL);
			photoProgressBar.setState(SWT.NORMAL);
			photoProgressBar.setSelection(0);

			/* duplicate logic done in web-upload-client */
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
			log("Upload failed on page " + currentPage.getTitle());
			overallProgressBar.setState(SWT.ERROR);
			photoProgressBar.setState(SWT.ERROR);
			result = new UploadResult(false, currentPage, currentPhoto);

			// TODO we could offer "retry" here if the error looks transient and
			// was not retried automatically?

			showError("uploading", "Upload failed. Some pages may not be fully uploaded. Fix the error and retry",
					error);

			// leave the shell open, allow user to read
			// the log and then close via cancel
			btnCancel.removeSelectionListener(cancelCurrentOperationListener);
			btnCancel.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					shell.close();
				}
			});
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

	public void setThumbnailProvider(ThumbnailProvider thumbnailProvider) {
		this.thumbnailProvider = thumbnailProvider;
	}

	public void setUploadClient(UploadClient client) {
		this.client = client;
	}
}
