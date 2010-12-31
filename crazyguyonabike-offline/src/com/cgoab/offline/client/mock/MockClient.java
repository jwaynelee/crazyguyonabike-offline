package com.cgoab.offline.client.mock;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cgoab.offline.client.AbstractUploadClient;
import com.cgoab.offline.client.CompletionCallback;
import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.DocumentType;
import com.cgoab.offline.client.PhotoUploadProgressListener;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.util.StringUtils;

public class MockClient extends AbstractUploadClient {

	private int nextPageID;

	private Map<Integer, Page> uploadedPages = new HashMap<Integer, Page>();

	private Map<String, Photo> uploadedPhotos = new HashMap<String, Photo>();

	private Map<Integer, DocumentDescription> documents = new HashMap<Integer, DocumentDescription>();

	private int delay = 100; // simulate delay

	protected String currentUsername;
	protected String currentRealname;

	public MockClient() {
		DocumentDescription d0 = new DocumentDescription("MyJournal 1", 0, "Not yet started", 100, DocumentType.JOURNAL);
		DocumentDescription d1 = new DocumentDescription("MyJournal 2", 0, "Not yet started", 200, DocumentType.JOURNAL);
		documents.put(d0.getDocumentId(), d0);
		documents.put(d1.getDocumentId(), d1);
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
	public void login(final String username, String password, CompletionCallback<String> callback) {
		asyncExec(new Task<String>(callback) {
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
	public void getDocuments(CompletionCallback<List<DocumentDescription>> callback) {
		asyncExec(new Task<List<DocumentDescription>>(callback) {
			@Override
			protected List<DocumentDescription> doRun() throws Exception {
				Thread.sleep(delay);
				return new ArrayList<DocumentDescription>(documents.values());
			}
		});
	}

	@Override
	public void createNewPage(final int documentId, final Page page, CompletionCallback<Integer> callback) {
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
				uploadedPages.put(pageId, (Page) page.clone());
				return pageId;
			}
		});
	}

	private void error(String message) {
		throw new RuntimeException(message);
	}

	private String getImageName(Photo photo) {
		// String name = photo.getImageName();
		// if (name != null && !"".equals(name)) {
		// return name;
		// }
		return photo.getFile().getName();
	}

	@Override
	public void addPhoto(final int pageId, final Photo photo, CompletionCallback<Void> callback,
			final PhotoUploadProgressListener progressListener) {
		asyncExec(new Task<Void>(callback) {
			@Override
			public Void doRun() throws Exception {
				// check page id is valid...
				Page page = uploadedPages.get(new Integer(pageId));
				if (page == null) {
					/* don't throw, might have restarted */
					// error("Unknown PageID");
				}
				String name = getImageName(photo);
				if (uploadedPhotos.containsKey(name)) {
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
					progressListener.uploadPhotoProgress(photo, i * 10, 100);
				}
				uploadedPhotos.put(name, photo);
				return null;
			}

			@Override
			protected void cancel() {
				getWorkerThread().interrupt();
			}
		});
	}

	@Override
	public void logout(CompletionCallback<Void> callback) {
		currentRealname = currentUsername = null;
		callback.onCompletion(null);
	}

	public void setOperationDelay(int delay) {
		this.delay = delay;
	}
}
