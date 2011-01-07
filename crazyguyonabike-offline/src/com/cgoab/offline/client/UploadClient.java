package com.cgoab.offline.client;

import java.io.File;
import java.util.List;

import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;

/**
 * An API that encapsulates the "Web Methods" (HTTP POST & GET messages) to
 * uploaded pages and photos to the CGOAB server.
 * <p>
 * Operations execute asynchronously however only one operation can ever be in
 * progress at a given time. An {@link IllegalStateException} will be thrown if
 * constraint is violated. Typically the callback passed into operations is used
 * to detect completion before proceeding with the next operation.
 */
public interface UploadClient {

	/**
	 * Attempts to cancel the currently running operation, if any.
	 */
	public void cancel();

	/**
	 * Logs into the CGOAB server.
	 * 
	 * @param username
	 *            username to log in with, if null will try to auto-login using
	 *            any previously saved cookie.
	 * @param password
	 *            password to log in with
	 * @param callback
	 *            callback to run when the operation completes
	 */
	public void login(String username, String password, CompletionCallback<String> callback);

	public String getCurrentUsername();

	public String getCurrentUserRealName();

	/**
	 * Loads a listing of the documents (journals & articles) authored by the
	 * current user.
	 * 
	 * @return
	 * @throws Exception
	 */
	public void getDocuments(CompletionCallback<List<DocumentDescription>> callback);

	/**
	 * Creates a new page at the end of the document identified by
	 * <tt>docId</tt> but does <b>NOT</b> upload any photos.
	 * 
	 * @param docId
	 * @param page
	 * @param callback
	 * @return
	 * @throws Exception
	 */
	public void createNewPage(int docId, Page page, CompletionCallback<Integer> callback);

	/**
	 * Adds a photo to the page identified by <tt>pageId</tt>, the optional
	 * progressListener will be notified of the upload progress.
	 * 
	 * @param pageId
	 * @param photo
	 * @param photoFile
	 *            an optional photo to use for the override, default
	 *            {@link Photo#getFile()} used if <tt>null</tt>
	 * @param callback
	 * @param progressListener
	 */
	public void addPhoto(int pageId, Photo photo, CompletionCallback<Void> callback,
			PhotoUploadProgressListener progressListener);

	/**
	 * Returns table of contents for a document
	 * 
	 * @param documentId
	 * @return
	 * @throws Exception
	 */
	// public List<TOCEntry> getTableOfContents(int documentId) throws
	// Exception;

	/**
	 * Logs out of the server, does nothing if not already logged in.
	 * 
	 * @param callback
	 */
	public void logout(CompletionCallback<Void> callback);

	// domain exception, thrown when the server replies with an error
	public static class ServerOperationException extends RuntimeException {
	}

	// thrown when a operation detects that the connection is no longer logged
	// in
	public static class NotLoggedInException extends ServerOperationException {
	}

	// thrown when a login attempt fails
	public static class FailedLoginException extends ServerOperationException {
	}

	// thrown when add page fails, perhaps because the date is older than
	// previous pages
	public static class FailedAddPageException extends ServerOperationException {
	}

	// thrown when add photo fails, perhaps because the photo has the same name
	// as another photo in the journal
	public static class FailedAddPhotoException extends ServerOperationException {
	}

	/**
	 * Disposes of any resources used by this service.
	 */
	public void dispose();
}