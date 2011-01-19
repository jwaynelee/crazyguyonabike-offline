package com.cgoab.offline.client.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.client.AbstractUploadClient;
import com.cgoab.offline.client.CompletionCallback;
import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.DocumentType;
import com.cgoab.offline.client.PhotoUploadProgressListener;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.ui.util.UIExecutor;
import com.cgoab.offline.util.StringUtils;

/**
 * Mock client, handy for testing the UI.
 */
public class MockClient extends AbstractUploadClient {

	protected String currentRealname;

	protected String currentUsername;

	private int delay = 50; // simulate upload delay

	private int documentId = -1;

	private Map<Integer, DocumentDescription> documents = new HashMap<Integer, DocumentDescription>();

	private int nextPageID;
	private Set<Integer> uploadedPages = new HashSet<Integer>();

	private Set<String> uploadedPhotos = new HashSet<String>();

	public MockClient() {
		super(new UIExecutor(Display.getCurrent()));
		DocumentDescription d0 = new DocumentDescription("MyJournal 1", 0, "Not yet started", 100, DocumentType.JOURNAL);
		DocumentDescription d1 = new DocumentDescription("MyJournal 2", 0, "Not yet started", 200, DocumentType.JOURNAL);
		documents.put(d0.getDocumentId(), d0);
		documents.put(d1.getDocumentId(), d1);
	}

	@Override
	public void addPhoto(final int pageId, final Photo photo, CompletionCallback<Void> callback,
			final PhotoUploadProgressListener progressListener) {
		throwIfNotInitialized();
		asyncExec(new Task<Void>(callback) {
			@Override
			protected void cancel() {
				getWorkerThread().interrupt();
			}

			@Override
			public Void doRun() throws Exception {
				// check page id is valid...
				boolean contains = uploadedPages.contains(new Integer(pageId));
				if (!contains) {
					/* don't throw, might have restarted */
					// error("Unknown PageID");
				}
				String name = getImageName(photo);
				if (uploadedPhotos.contains(name)) {
					error("Existing photo with name " + name);
				}
				String caption = photo.getCaption();
				if (caption != null && caption.toLowerCase().contains("error")) {
					error("MockPhotoError");
				}
				for (int i = 0; i <= 10; ++i) {
					if (delay > 0) {
						Thread.sleep(delay > 1 ? delay : 1);
					}
					final int done = i * 10;
					getCallbackExecutor().execute(new Runnable() {
						@Override
						public void run() {
							progressListener.uploadPhotoProgress(photo, done, 100);
						}
					});
				}
				uploadedPhotos.add(name);
				return null;
			}
		});
	}

	@Override
	public void createNewPage(final Page page, CompletionCallback<Integer> callback) {
		throwIfNotInitialized();
		asyncExec(new Task<Integer>(callback) {
			@Override
			public Integer doRun() throws Exception {
				if (!documents.containsKey(documentId)) {
					error("Unknown documentId " + documentId);
				}

				/* to help error testing, throw if titlt contains "error" */
				String title = page.getTitle();
				if (title != null && title.toLowerCase().contains("error")) {
					error("Mock Error!");
				}

				int pageId = nextPageID++;
				Thread.sleep(delay);
				uploadedPages.add(pageId);
				return pageId;
			}
		});
	}

	private void error(String message) {
		throw new RuntimeException(message);
	}

	@Override
	public String getCurrentUsername() {
		return currentUsername;
	}

	@Override
	public String getCurrentUserRealName() {
		return currentRealname;
	}

	@Override
	public void getDocuments(CompletionCallback<List<DocumentDescription>> callback) {
		asyncExec(new Task<List<DocumentDescription>>(callback) {
			@Override
			protected List<DocumentDescription> doRun() throws Exception {
				Thread.sleep(delay);
				return new ArrayList<DocumentDescription>(documents.values());
			}
		});
	}

	private String getImageName(Photo photo) {
		return photo.getFile().getName();
	}

	@Override
	public void initialize(final int docId, CompletionCallback<Void> callback) {
		asyncExec(new Task<Void>(callback) {
			@Override
			protected Void doRun() throws Exception {
				documentId = docId;
				return null;
			}
		});
	}

	@Override
	public void login(final String username, String password, CompletionCallback<String> callback) {
		asyncExec(new Task<String>(callback) {
			@Override
			protected String doRun() throws Exception {
				Thread.sleep(delay);
				if (username == null) {
					currentUsername = System.getProperty("user.name");
					currentRealname = "Mr " + StringUtils.capitalise(currentUsername);
				} else {
					currentUsername = username;
					currentRealname = username;
				}
				return currentUsername;
			}
		});
	}

	@Override
	public void logout(CompletionCallback<Void> callback) {
		currentRealname = currentUsername = null;
		documentId = -1;
		callback.onCompletion(null);
	}

	public void setOperationDelay(int delay) {
		this.delay = delay;
	}

	private void throwIfNotInitialized() {
		if (documentId == -1) {
			throw new IllegalStateException("Not initialized!");
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
