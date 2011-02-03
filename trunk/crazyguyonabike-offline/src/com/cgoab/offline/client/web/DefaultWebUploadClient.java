package com.cgoab.offline.client.web;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.CookieSpecFactory;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SetCookie;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;

import com.cgoab.offline.client.AbstractUploadClient;
import com.cgoab.offline.client.CompletionCallback;
import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.PhotoUploadProgressListener;
import com.cgoab.offline.client.UploadClient;
import com.cgoab.offline.client.web.AbstractFormBinder.InitializationErrorException;
import com.cgoab.offline.client.web.AbstractFormBinder.InitializationWarningException;
import com.cgoab.offline.client.web.ProgressTrackingFileBody.ProgressListener;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.util.Assert;
import com.cgoab.offline.util.FormFinder;
import com.cgoab.offline.util.FormFinder.HtmlForm;
import com.cgoab.offline.util.StringUtils;
import com.cgoab.offline.util.Utils;

/**
 * {@link UploadClient} that uses the traditional web (HTML over HTTP) interface
 * to the crazyguyonabike server.
 * <p>
 * This implementation is inherintly fragile. It relies on the URLs and form
 * fields used to add pages and photos not changing. This could potentially
 * change. Such a change would have no impact on users using the site in a
 * webbrowser as the site generates the forms that it expcets to be posted back
 * at it, however this application would suffer. As an added guard this
 * implementaton, during {@link #initialize(int, CompletionCallback)} , checks
 * if the HTML forms to add pages and photos have changed. If so it throws
 * {@link InitializationWarningException} if unused fields were removed or new
 * fields added (probably OK) and throws {@link InitializationErrorException} if
 * required fields are missing (definitly not OK).
 * <p>
 * The intention is that this class can be removed and a much simpler
 * implementation can be used that relies on a simple and stable published set
 * of HTTP operations designed for programatic use (ie, non HTML UI).
 * 
 * <h3>Mapping of Operations to HTTP/HTML</h3>
 * If not specified, form values default to those found in the initialization
 * phase.
 * <table border="1">
 * <tr>
 * <th>Operation</th>
 * <th>Implementation</th>
 * </tr>
 * <tr>
 * <td rowspan="4">Login</td>
 * <td>POST to "/login/index.html" with
 * <tt>[username,password,command="login",button="Login","persistent="0"]</tt></td>
 * </tr>
 * <tr>
 * <td>expected 200 (OK) (assume META redirect to "/my/") else error</td>
 * </tr>
 * <tr>
 * <td>GET from "/my/account/" to verify login</td>
 * </tr>
 * <tr>
 * <td>extract realname and username using
 * {@link CGOABHtmlUtils#getRealnameFromMyAccount(TagNode)} and
 * {@link CGOABHtmlUtils#getUsernameFromMyAccount(TagNode)}</td>
 * </tr>
 * <tr>
 * <td rowspan="3">GetDocuments</td>
 * <td>GET from "/my/"</td>
 * </tr>
 * <tr>
 * <td>expect 200 (OK) else error</td>
 * </tr>
 * <tr>
 * <td>extract documents from html using
 * {@link CGOABHtmlUtils#extractDocuments(TagNode)}</td>
 * </tr>
 * <tr>
 * <td rowspan="3">CreateNewPage</td>
 * <td>POST to "/doc/edit/page/" with <tt>[toc_heading_size, toc_heading_bold,
 * toc_heading_italic ,toc_level, visible, date, distance, title, headline,
 * text, edit_comment, format, doc_id, submit="Upload Pic or File"]</tt></td>
 * </tr>
 * <tr>
 * <td>expect 301 (redirect) else error</td>
 * </tr>
 * <tr>
 * <td>extract the new page_id from "Location" redirect header, match against
 * <tt>page_id=(\\d+)</tt></td>
 * </tr>
 * <tr>
 * <td rowspan="2">AddPhoto</td>
 * <td>POST to "/doc/edit/page/pic/" with
 * <tt>[upload_filename, caption, page_id]</tt></td>
 * </tr>
 * <tr>
 * <td>expect 301 (redirect) else error</td>
 * </tr>
 * </table>
 * 
 * <h3>Error handling</h3>
 * 
 * When an error occurs on the server the error message (if any) is returned to
 * the user embedded in the page. There is no consistent location for this error
 * message, it varies on the actual error that occured. So isolating the error
 * message is virtually impossible. Additionally, this client may detect an
 * error condition but the server will not know it was an error (for example,
 * the login page html will be returned if we try to perform an operation before
 * logging in). As a result this implementation provides the <i>entire</i> html
 * page returned from the server to the error handler via a
 * {@link ServerOperationException}. This can then be presented to the user
 * allowing them to find the error string.
 */
public class DefaultWebUploadClient extends AbstractUploadClient {

	private static final String MYSPEC = "myspec";
	private static final Pattern PAGE_ID_PATTERN = Pattern.compile(".*page_id=(\\d+).*");

	private static void assertStatusCode(String html, int actual, int... expected) {
		for (int i : expected) {
			if (i == actual) {
				return;
			}
		}
		throw new ServerOperationException("Unexpected staus code '" + actual + "' expected "
				+ Arrays.toString(expected), html);
	}

	private static HttpEntity createFormEntity(Map<String, Object> properties) throws UnsupportedEncodingException {
		List<NameValuePair> pairs = new ArrayList<NameValuePair>(properties.size());
		for (Entry<String, Object> e : properties.entrySet()) {
			Object value = e.getValue();
			if (value instanceof String) {
				pairs.add(new BasicNameValuePair(e.getKey(), (String) value));
			} else if (value == null) {
				/* ignore */
			} else {
				throw new IllegalArgumentException("Unhandled type " + value);
			}
		}
		return new UrlEncodedFormEntity(pairs);
	}

	private static HttpEntity createMultipartFormEntity(Map<String, Object> properties)
			throws UnsupportedEncodingException {
		MultipartEntity entity = new MultipartEntity();
		for (Entry<String, Object> e : properties.entrySet()) {
			Object value = e.getValue();
			ContentBody body;
			if (value instanceof ContentBody) {
				body = (ContentBody) value;
			} else if (value instanceof String) {
				body = new StringBody((String) value);
			} else {
				throw new IllegalArgumentException("Unhandled type " + value);
			}
			entity.addPart(e.getKey(), body);
		}
		return entity;
	}

	static boolean isRedirect(int code) {
		return code == HttpStatus.SC_MOVED_PERMANENTLY || code == HttpStatus.SC_MOVED_TEMPORARILY;
	}

	static void printResponse(HttpResponse response) {
		System.out.println("-------------------------------------");
		System.out.println("Status: " + response.getStatusLine());
		for (Header h : response.getAllHeaders()) {
			System.out.println(h.getName() + "=" + h.getValue());
		}
		System.out.println("-------------------------------------");
	}

	private static void useMyCookiePolicy(HttpRequest request) {
		HttpClientParams.setCookiePolicy(request.getParams(), MYSPEC);
	}

	private HtmlCleaner cleaner;

	private DefaultHttpClient client;

	private BasicHttpContext context;

	private CookieStore cookies;

	private String currentRealname;

	private String currentUsername;

	// public void delete(int pageId) {
	//
	// //http://www.crazyguyonabike.com/?command=do_delete&page_id=173278&doc_id=&from=editcontents
	// HttpGet get = new HttpGet(createURI("doc/edit/page/", ))
	// }

	private final String host;

	private PageBinder pageBinder;

	private PhotoBinder photoBinder;

	private final int port;

	boolean validatedAddPageForm;

	/**
	 * Creates a new client
	 * 
	 * @param url
	 *            url of the server
	 * @param port
	 *            port of the server
	 * @param cookies
	 *            place to store cookies set by the server
	 * @param executor
	 *            executor used to run completion callbacks, if null calls on
	 *            the client thread
	 */
	public DefaultWebUploadClient(String url, int port, CookieStore cookies, Executor executor) {
		super(executor);
		this.host = url;
		this.port = port;
		this.cleaner = new HtmlCleaner();
		this.client = new DefaultHttpClient();
		client.setHttpRequestRetryHandler(new UnlimitedExponentialBackoffRetry());
		client.getParams().setBooleanParameter("http.protocol.handle-redirects", Boolean.FALSE);

		/* identify ourselves to CGOAB using ${name}/${version} */
		String version = Utils.getSpecificationVersion(DefaultWebUploadClient.class);
		String name = Utils.getImplementationTitleString(DefaultWebUploadClient.class);
		HttpProtocolParams.setUserAgent(client.getParams(), name + "/" + version);

		this.context = new BasicHttpContext();
		/**
		 * Add a custom cookie handler for "-1" expiry
		 */
		client.getCookieSpecs().register(MYSPEC, new CookieSpecFactory() {
			@Override
			public CookieSpec newInstance(HttpParams params) {
				return new MyCookieSpec();
			}
		});

		this.cookies = cookies;
		// new FileCookieStore(cookiePath);
		context.setAttribute(ClientContext.COOKIE_STORE, cookies);
		CleanerProperties props = cleaner.getProperties();
		props.setAllowHtmlInsideAttributes(true);
		props.setAllowMultiWordAttributes(true);
		props.setRecognizeUnicodeChars(true);
		props.setAdvancedXmlEscape(true);
		props.setOmitComments(true);
	}

	@Override
	public void addPhoto(final int pageId, final Photo photo, CompletionCallback<Void> callback,
			PhotoUploadProgressListener progressListener) {
		throwIfNotInitialized();
		final PhotoUploadProgressListener listener = progressListener == null ? new PhotoUploadProgressAdapter()
				: progressListener;
		asyncExec(new AddPhotoTask(callback, pageId, photo, listener));
	}

	@Override
	public void createNewPage(Page page, CompletionCallback<Integer> callback) {
		throwIfNotInitialized();
		asyncExec(new AddPageTask(callback, page));
	}

	private URI createURI(String path, String query) throws URISyntaxException {
		return URIUtils.createURI("http", host, port, path, query, null);
	}

	public void deletePage(final int pageId, CompletionCallback<Void> callback) {
		asyncExec(new CancellableHttpTask<Void>(callback) {
			@Override
			protected Void doRun() throws Exception {
				doDeletePageGET(pageId, this);
				return null;
			}
		});
	}

	@Override
	public void dispose() {
		// don't bother to logout, just null data and kill worker
		currentRealname = currentUsername = null;
		client.getConnectionManager().shutdown();
		super.dispose();
	}

	/**
	 * Exposed for testing
	 * 
	 * @param pageId
	 * @param task
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	private void doDeletePageGET(int pageId, CancellableHttpTask<?> task) throws IOException, URISyntaxException {
		HttpGet deletePage = new HttpGet(createURI("/doc/edit/page/", "command=do_delete&page_id=" + pageId
				+ "&from=editcontents"));
		HttpResponse response = task.execute(deletePage);
		String html = EntityUtils.toString(response.getEntity());
		int code = response.getStatusLine().getStatusCode();
		if (code != HttpStatus.SC_MOVED_PERMANENTLY) {
			throw new ServerOperationException("Unexpected response code [" + code + "] after delete", html);
		}
	}

	private void doLogoutGET(CancellableHttpTask<?> task) throws URISyntaxException, ClientProtocolException,
			IOException {
		HttpGet get = new HttpGet(createURI("/logout/", null));
		useMyCookiePolicy(get); // "expire=-1" cookie only set on logout
		HttpResponse result = task.execute(get);
		String html = EntityUtils.toString(result.getEntity());
		assertStatusCode(html, result.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
		return;
	}

	// for testing
	public CookieStore getCookieStore() {
		return cookies;
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
		asyncExec(new GetDocumentsTask(callback));
	}

	@Override
	public void initialize(int docId, final CompletionCallback<Void> callback) {
		asyncExec(new InitializeFormsTask(callback, docId));
	}

	@Override
	public void login(final String username, final String password, CompletionCallback<String> callback) {
		asyncExec(new LoginTask(callback, username, password));
	}

	@Override
	public void logout(CompletionCallback<Void> callback) {
		asyncExec(new LogoutTask(callback));
		currentRealname = currentUsername = null;
		photoBinder = null;
		pageBinder = null;
	}

	private void throwIfNotInitialized() {
		if (photoBinder == null || pageBinder == null || !photoBinder.isInitialized() || !pageBinder.isInitialized()) {
			throw new IllegalStateException("UploadClient not initialized!");
		}
	}

	//
	// public List<TOCEntry> getTableOfContents(int journalId) throws Exception
	// {
	//
	// HttpGet g2 = new HttpGet(createURI("/doc/edit/", "doc_id=" + journalId));
	//
	// HttpResponse contents = client.execute(g2, context);
	// TagNode n1 = cleaner.clean(contents.getEntity().getContent());
	//
	// List<TOCEntry> toc = new ArrayList<TOCEntry>();
	//
	// // [0] is header
	// Object[] entries = n1.evaluateXPath("/body/table[3]/tbody/tr");
	// for (int i = 1; i < entries.length; ++i) {
	// TagNode t = (TagNode) entries[i];
	// TagNode[] es = t.getElementsByName("th", true);
	// String sequence = clean(es[0].getText().toString());
	// String indent = clean(es[1].getText().toString());
	// es = t.getElementsByName("td", true);
	//
	// // unwrap "hx" wrapper
	// Object[] aas = es[0].evaluateXPath("//a");
	// List<Object> childTags = ((TagNode) aas[0]).getParent().getChildren();
	//
	// // find 2nd "a" link and merge string
	// String text = "";
	// TagNode a = null;
	// int ai = 0;
	// for (Iterator<Object> j = childTags.iterator(); j.hasNext();) {
	// Object o = j.next();
	// if (o instanceof TagNode) {
	// TagNode tn = (TagNode) o;
	// if (tn.getName().equals("a")) {
	// if (++ai == 2) {
	// a = tn;
	// }
	// }
	// } else if (o instanceof ContentToken) {
	// text += o.toString();
	// } else if (o instanceof EndTagToken) { // ignore } else { throw
	// new IllegalStateException("Unexpected: " + o.getClass());
	// }
	// }
	//
	// text = clean(text);
	//
	// // trim ":"
	// String description = text.length() > 0 ? text.substring(1) : text;
	//
	// String title = clean(a.getText().toString());
	// int id = getId(a.getAttributeByName("href"), PAGE_ID);
	// toc.add(new TOCEntry(title, description, Integer.parseInt(sequence),
	// Integer.parseInt(indent), id));
	// }
	// return toc;
	// }

	void toFile(String name, HttpEntity entity) throws IOException {
		FileWriter file = new FileWriter(name);
		file.write(EntityUtils.toString(entity));
		file.close();
	}

	@Override
	public String toString() {
		return host + (port == -1 ? "" : ":" + port);
	}

	class AddPageTask extends CancellableHttpTask<Integer> {
		private Page page;

		public AddPageTask(CompletionCallback<Integer> callback, Page page) {
			super(callback);
			this.page = page;
		}

		@Override
		protected Integer doRun() throws Exception {
			HttpPost post = new HttpPost(createURI("/doc/edit/page/", null));
			post.setEntity(createFormEntity(pageBinder.bind(page)));
			HttpResponse result = execute(post);
			String html = EntityUtils.toString(result.getEntity());
			int statusCode = result.getStatusLine().getStatusCode();
			if (isRedirect(statusCode)) {
				/* page-id should be in the redirect header */
				result.getEntity().consumeContent();
				Header locationHeader = result.getFirstHeader("Location");
				if (locationHeader == null) {
					throw new IllegalStateException("No location header returned by server.");
				}
				String location = locationHeader.getValue();
				Matcher match = PAGE_ID_PATTERN.matcher(location);
				if (!match.matches()) {
					throw new ServerOperationException("Could not find page_id in url [" + location + "]", html);
				}
				return Integer.parseInt(match.group(1));
			} else if (statusCode == HttpStatus.SC_OK) {
				/* page html is the error message */
				throw new ServerOperationException("Failed to add page", html);
			} else {
				throw new IllegalStateException("Unexpected status code [" + statusCode + "]");
			}
		}
	}

	class AddPhotoTask extends CancellableHttpTask<Void> {

		private PhotoUploadProgressListener listener;
		private int pageId;
		private Photo photo;

		public AddPhotoTask(CompletionCallback<Void> callback, int pageId, Photo photo,
				PhotoUploadProgressListener listener) {
			super(callback);
			this.listener = listener;
			this.photo = photo;
			this.pageId = pageId;
		}

		@Override
		public Void doRun() throws ClientProtocolException, IOException, URISyntaxException {
			HttpPost post = new HttpPost(createURI("doc/edit/page/pic/", null));
			File file = photo.getResizedPhoto();
			if (file == null) {
				file = photo.getFile();
			}
			FileBody fb = new ProgressTrackingFileBody(file, new ProgressListener() {
				@Override
				public void transferred(final long sent, final long total) {
					getCallbackExecutor().execute(new Runnable() {
						@Override
						public void run() {
							listener.uploadPhotoProgress(photo, sent, total);
						}
					});
				}
			});

			post.setEntity(createMultipartFormEntity(photoBinder.bind(photo, fb, pageId)));
			HttpResponse response = execute(post);
			String html = EntityUtils.toString(response.getEntity());

			int statusCode = response.getStatusLine().getStatusCode();
			if (isRedirect(statusCode)) {
				/* TODO assert is a redirect to /doc/edit/page/? */
				return null;
			} else if (statusCode == HttpStatus.SC_OK) {
				throw new ServerOperationException("Error adding photo " + photo.getFile(), html);
			} else {
				throw new IllegalStateException("Unexpected status code [" + statusCode + "]");
			}
		}
	}

	abstract class CancellableHttpTask<T> extends Task<T> {

		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final AtomicReference<HttpRequestBase> currentOperation = new AtomicReference<HttpRequestBase>();

		protected CancellableHttpTask(CompletionCallback<T> callback) {
			super(callback);
		}

		@Override
		protected final void cancel() {
			cancelled.set(true);
			HttpRequestBase request = currentOperation.get();
			if (request != null) {
				request.abort();
			}
		}

		protected HttpResponse execute(HttpRequestBase request) throws ClientProtocolException, IOException {
			if (cancelled.get()) {
				throw new CancellationException("Task is cancelled!");
			}
			try {
				currentOperation.set(request);
				return client.execute(request, context);
			} finally {
				currentOperation.set(null);
			}
		}

		public boolean isCancelled() {
			return cancelled.get();
		}
	}

	class GetDocumentsTask extends CancellableHttpTask<List<DocumentDescription>> {

		public GetDocumentsTask(CompletionCallback<List<DocumentDescription>> callback) {
			super(callback);
		}

		@Override
		protected List<DocumentDescription> doRun() throws Exception {
			HttpGet get = new HttpGet(createURI("/my/", null));
			HttpResponse response = execute(get);
			String html = EntityUtils.toString(response.getEntity());
			assertStatusCode(html, response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
			return CGOABHtmlUtils.extractDocuments(cleaner.clean(html));
		}
	}

	class InitializeFormsTask extends CancellableHttpTask<Void> {

		private final int documentId;

		public InitializeFormsTask(CompletionCallback<Void> callback, int docId) {
			super(callback);
			this.documentId = docId;
		}

		@Override
		protected Void doRun() throws Exception {
			// 1) load edit-page form
			LOG.debug("Initializing client using document {}", documentId);
			HttpGet getEditPage = new HttpGet(createURI("/doc/edit/page/", "command=add&doc_id=" + documentId
					+ "&from=editcontents"));

			HttpResponse response = execute(getEditPage);

			String html = EntityUtils.toString(response.getEntity());
			assertStatusCode(html, response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
			TagNode root = cleaner.clean(html);
			HtmlForm pageForm = FormFinder.findFormWithName(root, "form");
			boolean fatal = false;
			List<String> errorMessages = new ArrayList<String>();
			if (pageForm == null) {
				/* error */
				fatal = true;
				errorMessages.add("ERROR: EditPage form missing");
			} else {
				/* throws if we have a problem */
				LOG.debug("Found edit-page form {}, validating", pageForm);
				pageBinder = new PageBinder();
				pageBinder.setDocumentId(documentId);
				if (!pageBinder.initialize(pageForm, errorMessages)) {
					fatal = true;
				}
			}

			// 2) post the form to get to add-photo form
			HttpPost postEditPhoto = new HttpPost(createURI("/doc/edit/page/", null));
			Map<String, Object> overrides = new HashMap<String, Object>();
			overrides.put("visible", "false");
			overrides.put("title", "__VALIDATE_CGOAB_OFFLINE__");
			overrides.put("headline", "__DELETE_THIS_PAGE__");
			overrides.put("format", "auto");
			overrides.put("text", "");
			overrides.put("date", ""); /* don't fail because date is wrong! */
			overrides.put("submit", "Upload Pic or File");
			/* not needed, but handy for testing */
			overrides.put("doc_id", "" + documentId);
			postEditPhoto.setEntity(createFormEntity(pageForm.newMergedProperties(overrides)));
			// FormItem pageId = null;
			int pageId = 0;
			try {
				LOG.debug("Creating temporary page to check add-photo form");
				response = execute(postEditPhoto);
				html = EntityUtils.toString(response.getEntity());
				assertStatusCode(html, response.getStatusLine().getStatusCode(), HttpStatus.SC_MOVED_PERMANENTLY);
				// TODO turn on follow redirects?
				Header location = response.getFirstHeader("Location");
				pageId = CGOABHtmlUtils.getPageId(location.getValue());
				HttpGet get = new HttpGet(createURI(location.getValue(), null));
				response = execute(get);
				html = EntityUtils.toString(response.getEntity());
				root = cleaner.clean(html);
				HtmlForm photoForm = FormFinder.findFormWithName(root, "form");// "/doc/edit/page/pic/");
				if (photoForm == null) {
					fatal = true;
					errorMessages.add("ERROR: AddPhoto form missing");
				} else {
					LOG.debug("Found add-photo form {}", photoForm);
					// pageId = photoForm.getItems().get("page_id");
					photoBinder = new PhotoBinder();
					if (!photoBinder.initialize(photoForm, errorMessages)) {
						fatal = true;
					}
				}
			} finally {
				// 3) delete the temporary page we just created
				try {
					if (pageId != 0) {
						try {
							LOG.debug("Deleting temporary page {}", pageId);
							doDeletePageGET(pageId, this);
							LOG.debug("Deleted temporary page {}", pageId);
						} catch (ServerOperationException e) {
							LOG.warn("Exception deleting temporary page, it may need to be deleted manually", e);
						}
					}
				} catch (Throwable e) {
					LOG.warn("Unexpected exception deleting temporary page, it may need to be deleted manually", e);
				}
			}

			if (fatal) {
				throw new InitializationErrorException(StringUtils.join(errorMessages, "\n"));
			} else if (errorMessages.size() > 0) {
				throw new InitializationWarningException(StringUtils.join(errorMessages, "\n"));
			}

			return null;
		}
	}

	class LoginTask extends CancellableHttpTask<String> {

		private static final String LOGIN_PATH = "/login/index.html";
		final String password;
		final String username;

		public LoginTask(CompletionCallback<String> callback, String username, String password) {
			super(callback);
			this.username = username;
			this.password = password;
		}

		private String doLoginPOST(String username, String password) throws ClientProtocolException, IOException,
				URISyntaxException, XPatherException {
			LoginBinder binder = initializeBinder();
			HttpPost post = new HttpPost(createURI(LOGIN_PATH, null));
			post.setEntity(createFormEntity(binder.bind(username, password)));
			HttpResponse result = execute(post);
			String html = EntityUtils.toString(result.getEntity());
			assertStatusCode(html, result.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
			return html;
		}

		/**
		 * Attempts to get the current username from the "/my/account" page,
		 * returns null if not logged in.
		 * 
		 * @return
		 * @throws IOException
		 * @throws URISyntaxException
		 */
		private String doMyAccountGET() throws IOException, URISyntaxException {
			HttpGet get = new HttpGet(createURI("/my/account/", null));
			HttpResponse result = execute(get);
			String html = EntityUtils.toString(result.getEntity());
			if (result.getStatusLine().getStatusCode() == HttpStatus.SC_MOVED_PERMANENTLY) {
				return null; /* not logged in */
			}
			assertStatusCode(html, result.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
			return html;
		}

		@Override
		protected String doRun() throws Exception {
			LOG.debug("Checking if already logged in");
			String myAccountHtml = doMyAccountGET();

			if (username == null && myAccountHtml == null) {
				/* not logged in & no username supplied */
				LOG.debug("Not logged in and no username given");
				throw new ServerOperationException("Failed to auto-login", null);
			}

			if (myAccountHtml != null) {
				/* already logged in */
				TagNode myAccount = cleaner.clean(myAccountHtml);
				String loggedInAs = CGOABHtmlUtils.getUsernameFromMyAccount(myAccount);
				LOG.debug("Already logged in as [" + loggedInAs + "]");
				if (username != null && !username.equals(loggedInAs)) {
					/* already logged in as someone else, logout first */
					doLogoutGET(this);
				} else {
					/* logged in as expected user */
					currentUsername = loggedInAs;
					currentRealname = CGOABHtmlUtils.getRealnameFromMyAccount(myAccount);
					return loggedInAs;
				}
			} else {

			}

			LOG.debug("Logging in as {}", username);
			String loginHtml = doLoginPOST(username, password);

			// we could test post result for:
			//
			// Success: "Logging in xyz..." page, (META redirect)
			// Failure: "Password incorrect"
			//
			// to be more flexible just (re)check result of /my/

			myAccountHtml = doMyAccountGET();
			if (myAccountHtml == null) {
				/* still not logged in */
				LOG.debug("Failed to log in");
				throw new ServerOperationException("Failed to login with username '" + username + "'", loginHtml);
			}
			TagNode myAccount = cleaner.clean(myAccountHtml);
			currentUsername = CGOABHtmlUtils.getUsernameFromMyAccount(myAccount);
			Assert.equals(currentUsername, username);
			currentRealname = CGOABHtmlUtils.getRealnameFromMyAccount(myAccount);
			LOG.debug("Sucessfully logged in as {} ({})", currentUsername, currentRealname);
			return username;
		}

		private LoginBinder initializeBinder() throws URISyntaxException, ClientProtocolException, IOException,
				XPatherException {
			HttpGet get = new HttpGet(createURI(LOGIN_PATH, null));
			HttpResponse response = execute(get);
			String html = EntityUtils.toString(response.getEntity());
			assertStatusCode(html, response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
			HtmlForm form = FormFinder.findFormWithName(cleaner.clean(html), "form");
			if (form == null) {
				throw new InitializationErrorException("No [form] found");
			} else {
				LOG.debug("Found login-form {}", form);
			}
			LoginBinder binder = new LoginBinder();
			List<String> errors = new ArrayList<String>();
			if (!binder.initialize(form, errors)) {
				throw new InitializationErrorException(StringUtils.join(errors, "\n"));
			} else if (errors.size() > 0) {
				throw new InitializationWarningException(StringUtils.join(errors, "\n"));
			}
			return binder;
		}
	}

	class LogoutTask extends CancellableHttpTask<Void> {

		public LogoutTask(CompletionCallback<Void> callback) {
			super(callback);
		}

		@Override
		protected Void doRun() throws Exception {
			doLogoutGET(this);
			return null;
		}
	}

	private class MyCookieSpec extends BrowserCompatSpec {
		public MyCookieSpec() {
			super();
			final CookieAttributeHandler defaultHandler = getAttribHandler(ClientCookie.EXPIRES_ATTR);
			registerAttribHandler(ClientCookie.EXPIRES_ATTR, new CookieAttributeHandler() {

				@Override
				public boolean match(Cookie cookie, CookieOrigin origin) {
					return defaultHandler.match(cookie, origin);
				}

				@Override
				public void parse(SetCookie cookie, String value) throws MalformedCookieException {
					if ("-1".equals(value)) {
						// copy chrome and expire in 10 years...
						Calendar c = Calendar.getInstance();
						c.add(Calendar.YEAR, 10);
						cookie.setExpiryDate(c.getTime());
						LOG.debug("Cookie expiration = -1, resetting as 10 years");
					} else {
						defaultHandler.parse(cookie, value);
					}
				}

				@Override
				public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
					defaultHandler.validate(cookie, origin);
				}
			});
		}
	}

	/* no-op implementation */
	private static class PhotoUploadProgressAdapter implements PhotoUploadProgressListener {
		@Override
		public void uploadPhotoProgress(Photo photo, long bytes, long total) {
		}
	}

	public static class ServerOperationException extends RuntimeException implements HtmlProvider {
		private static final long serialVersionUID = 1L;
		private final String html;

		public ServerOperationException(String message, String html) {
			super(message);
			this.html = html;
		}

		@Override
		public String getHtml() {
			return html;
		}
	}

	private class UnlimitedExponentialBackoffRetry extends DefaultHttpRequestRetryHandler {

		public UnlimitedExponentialBackoffRetry() {
			super(Integer.MAX_VALUE, false);
		}

		/* 50ms then 1, 2, 5 seconds */
		private long computeSleep(int executionCount) {
			switch (executionCount) {
			case 0:
			case 1:
				return 50;
			case 2:
				return 1000;
			case 3:
				return 2000;
			default:
				return 5000;
			}
		}

		@Override
		public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
			fireOnRetry(exception, executionCount);
			try {
				long sleep = computeSleep(executionCount);
				LOG.info("Sleeping for {}ms after failed execution", sleep);
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				/* ignore */
			}
			return super.retryRequest(exception, executionCount, context);
		}
	}
}