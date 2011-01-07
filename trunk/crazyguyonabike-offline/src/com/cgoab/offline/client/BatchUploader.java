package com.cgoab.offline.client;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.util.Assert;

/**
 * Provides the strategy to upload multiple pages, updating the model as
 * appropriate when an error is encountered.
 * 
 * This implementation will retry if presented with a failed upload.
 * Specifically, it detects {@link UploadState#PARTIALLY_UPLOAD} pages and skips
 * all of their {@link UploadState#UPLOADED} photos.
 * 
 * All operations are asynchronous to ease integration with a UI.
 */
public class BatchUploader {
	private static Logger LOG = LoggerFactory.getLogger(BatchUploader.class);
	private final UploadClient client;
	private final List<Page> uploadedPages = new ArrayList<Page>();
	private final List<Page> remainingPages = new ArrayList<Page>();
	private final List<Photo> uploadedPhotos = new ArrayList<Photo>();
	private final List<Photo> remainingPhotos = new ArrayList<Photo>();
	private Page currentPage;
	private Photo currentPhoto;
	private int documentId;

	private PhotoUploadProgressListener photoListener = new PhotoUploadProgressListener() {

		@Override
		public void uploadPhotoProgress(Photo photo, long bytes, long total) {
			fireUploadPhotoProgress(photo, bytes, total);
		}
	};

	private CompletionCallback<Void> photoCompletionCallback = new CompletionCallback<Void>() {

		@Override
		public void retryNotify(Throwable exception, int retryCount) {
			fireRetryPhoto(currentPhoto, exception, retryCount);
		}

		@Override
		public void onError(Throwable exception) {
			currentPhoto.setState(UploadState.ERROR);
			currentPage.setState(UploadState.PARTIALLY_UPLOAD);
			fireFinishedWithError(uploadedPages, remainingPages, currentPage, currentPhoto, exception);
		}

		@Override
		public void onCompletion(Void result) {
			uploadedPhotos.add(currentPhoto);
			remainingPhotos.remove(currentPhoto);
			fireAfterUploadPhoto(currentPhoto);
			currentPhoto.setState(UploadState.UPLOADED);
			currentPhoto = null;
			addNextPhoto();
		}
	};

	private CompletionCallback<Integer> pageCompletionCallback = new CompletionCallback<Integer>() {

		@Override
		public void retryNotify(Throwable exception, int retryCount) {
			fireRetryPage(currentPage, exception, retryCount);
		}

		@Override
		public void onError(Throwable exception) {
			currentPage.setState(UploadState.ERROR);
			fireFinishedWithError(uploadedPages, remainingPages, currentPage, null, exception);
		}

		@Override
		public void onCompletion(Integer result) {
			currentPage.setServerId(result.intValue());
			fireAfterUploadPage(currentPage);
			// update upload state now, since a failure might leave the page
			// with state ERROR
			currentPage.setState(UploadState.PARTIALLY_UPLOAD);
			initPhotosForCurrentPage();
			addNextPhoto();
		}
	};

	private void initPhotosForCurrentPage() {

		// init the photo structures
		remainingPhotos.clear();
		uploadedPhotos.clear();

		List<Photo> photos = currentPage.getPhotos();
		if (currentPage.getState() == UploadState.PARTIALLY_UPLOAD) {
			for (int i = 0; i < photos.size(); ++i) {
				Photo p = photos.get(i);
				if (p.getState() != UploadState.UPLOADED) {
					remainingPhotos.add(p); // ERROR or NEW
				}
			}
		} else {
			remainingPhotos.addAll(photos);
		}

	}

	private void addNextPhoto() {
		if (remainingPhotos.size() > 0) {
			currentPhoto = remainingPhotos.get(0);
			fireBeforeUploadPhoto(currentPhoto);
			LOG.info("Starting upload of [{}]", currentPhoto);
			client.addPhoto(currentPage.getServerId(), currentPhoto, photoCompletionCallback, photoListener);
		} else {
			// the page is now completely uploaded
			currentPage.setState(UploadState.UPLOADED);
			uploadedPages.add(currentPage);
			uploadNextPage();
		}
	}

	public BatchUploader(UploadClient client) {
		this.client = client;
	}

	public void setDocumentId(int documentId) {
		this.documentId = documentId;
	}

	public void setPages(List<Page> pages) {
		remainingPages.clear();
		remainingPages.addAll(pages);
	}

	// initiates the upload
	public void start() {
		Assert.isTrue(documentId > 0, "documentId is unset");
		LOG.info("Starting batch upload of {} pages to docId {}", remainingPages.size(), documentId);
		uploadedPages.clear();
		uploadNextPage();
	}

	private void uploadNextPage() {
		if (remainingPages.size() > 0) {
			currentPage = remainingPages.remove(0);
			if (currentPage.getState() == UploadState.PARTIALLY_UPLOAD) {
				// start with first failed photo...
				initPhotosForCurrentPage();
				addNextPhoto();
			} else {
				fireBeforeUploadPage(currentPage);
				client.createNewPage(documentId, currentPage, pageCompletionCallback);
			}
		} else {
			// complete!
			currentPage = null;
			fireFinished(uploadedPages);
		}
	}

	private void fireFinishedWithError(List<Page> pagesThatUploaded, List<Page> pagesNotUploaded, Page currentPage,
			Photo currentPhoto, Throwable error) {
		LOG.info("Upload failed due to [{}]", error.getMessage());
		listener.finishedWithError(pagesThatUploaded, pagesNotUploaded, currentPage, currentPhoto, error);
	}

	private void fireFinished(List<Page> uploaded) {
		LOG.info("Upload of {} pages complete", uploaded.size());
		listener.finished(uploaded);
	}

	private static String getShortDescription(Page page) {
		return page.getTitle() + " : " + page.getHeadline();
	}

	private void fireBeforeUploadPage(Page page) {
		LOG.info("Starting upload of page #{} [{}]", page.getLocalId(), getShortDescription(page));
		listener.beforeUploadPage(page);
	}

	private void fireAfterUploadPage(Page page) {
		LOG.info("Finished upload of page #{}", page.getLocalId());
		listener.afterUploadPage(page);
	}

	private void fireBeforeUploadPhoto(Photo photo) {
		listener.beforeUploadPhoto(photo);
	}

	private void fireAfterUploadPhoto(Photo photo) {
		listener.afterUploadPhoto(photo);
	}

	private void fireRetryPage(Page page, Throwable ex, int count) {
		listener.retryPage(page, ex, count);
	}

	private void fireRetryPhoto(Photo photo, Throwable ex, int count) {
		listener.retryPhoto(photo, ex, count);
	}

	public void fireUploadPhotoProgress(Photo photo, long bytes, long total) {
		listener.uploadPhotoProgress(photo, bytes, total);
	}

	private BatchUploaderListener listener;

	public void setListener(BatchUploaderListener listener) {
		this.listener = listener;
	}

	public interface BatchUploaderListener {
		public abstract void finishedWithError(List<Page> pagesThatUploaded, List<Page> pagesNotUploaded, Page current,
				Photo currentPhoto, Throwable error);

		public abstract void finished(List<Page> uploaded);

		public abstract void beforeUploadPage(Page page);

		public abstract void afterUploadPage(Page page);

		public abstract void beforeUploadPhoto(Photo photo);

		public abstract void afterUploadPhoto(Photo photo);

		public abstract void retryPhoto(Photo photo, Throwable error, int retryCount);

		public abstract void retryPage(Page page, Throwable error, int retryCount);

		public abstract void uploadPhotoProgress(Photo photo, long bytes, long total);
	}
}