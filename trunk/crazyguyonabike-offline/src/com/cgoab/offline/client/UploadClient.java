package com.cgoab.offline.client;

import java.util.List;

import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;

/**
 * An API that encapsulates the "operations" needed to uploaded pages and photos
 * to the CGOAB server.
 * <p>
 * Operations execute asynchronously however only one operation can ever be in
 * progress at a given time. An {@link IllegalStateException} is thrown if this
 * constraint is violated. Typically the callback passed into an operation will
 * be used to listen for completion before proceeding with the next operation
 * and so on.
 */
public interface UploadClient {

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
	 * Attempts to cancel the current operation, if any.
	 */
	public void cancel();

	/**
	 * Creates a new page at the end of the document that the client is bound
	 * too ( {@link #initialize(int, CompletionCallback)} must be called first)
	 * but does <b>NOT</b> upload any photos.
	 * 
	 * @param page
	 * @param callback
	 * @return
	 * @throws Exception
	 */
	public void createNewPage(Page page, CompletionCallback<Integer> callback);

	/**
	 * Disposes of any resources used by the client.
	 */
	public void dispose();

	public String getCurrentUsername();

	public String getCurrentUserRealName();

	/**
	 * Loads document listing for current user.
	 * 
	 * @return
	 * @throws Exception
	 */
	public void getDocuments(CompletionCallback<List<DocumentDescription>> callback);

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
	 * Initialises the upload client and binds to the given document, this will
	 * fail if the client detects any changes to the html forms hosted on the
	 * server.
	 */
	public void initialize(int documentId, CompletionCallback<Void> callback);

	/**
	 * Logs into the CGOAB server.
	 * 
	 * @param username
	 *            username to use. If <tt>null</tt> attempts to auto-login using
	 *            previously saved cookie.
	 * @param password
	 *            password to use
	 * @param callback
	 *            callback to invoke with the logged in username or exception
	 */
	public void login(String username, String password, CompletionCallback<String> callback);

	// // domain exception, thrown when the server replies with an error
	// public static class ServerOperationException extends RuntimeException {
	// }
	//
	// // thrown when a login attempt fails
	// public static class FailedLoginException extends ServerOperationException
	// {
	// }
	//
	// // thrown when add page fails, perhaps because the date is older than
	// // previous pages
	// public static class FailedAddPageException extends
	// ServerOperationException {
	// }
	//
	// // thrown when add photo fails, perhaps because the photo has the same
	// name
	// // as another photo in the journal
	// public static class FailedAddPhotoException extends
	// ServerOperationException {
	// }

	/**
	 * Logs out of the server, does nothing if not already logged in.
	 * 
	 * @param callback
	 */
	public void logout(CompletionCallback<Void> callback);
}