package com.cgoab.offline.client.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
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
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.client.AbstractUploadClient;
import com.cgoab.offline.client.CompletionCallback;
import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.DocumentType;
import com.cgoab.offline.client.PhotoFileResolver;
import com.cgoab.offline.client.PhotoUploadProgressListener;
import com.cgoab.offline.client.impl.ProgressTrackingFileBody.ProgressListener;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.util.StringUtils;

public class DefaultWebUploadClient extends AbstractUploadClient {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultWebUploadClient.class);
	private static final String MYSPEC = "myspec";
	private static final String CRAZYGUYONABIKE_URL = "www.crazyguyonabike.com";
	private static final Pattern DOC_ID_PATTERN = Pattern.compile(".*doc_id=(\\d+).*");
	private static final Pattern PAGE_ID_PATTERN = Pattern.compile(".*page_id=(\\d+).*");

	private DefaultHttpClient client;
	private HtmlCleaner cleaner;
	private BasicHttpContext context;
	private FileCookieStore cookies;
	private String currentUsername;
	private String currentRealname;
	private final PhotoFileResolver fileResolver;

	private static class MyCookieSpec extends BrowserCompatSpec {
		public MyCookieSpec() {
			super();
			final CookieAttributeHandler defaultHandler = getAttribHandler(ClientCookie.EXPIRES_ATTR);
			registerAttribHandler(ClientCookie.EXPIRES_ATTR, new CookieAttributeHandler() {

				@Override
				public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
					defaultHandler.validate(cookie, origin);
				}

				@Override
				public void parse(SetCookie cookie, String value) throws MalformedCookieException {
					if ("-1".equals(value)) {
						// copy chrome and expire in 10 years...
						Calendar c = Calendar.getInstance();
						c.add(Calendar.YEAR, 10);
						cookie.setExpiryDate(c.getTime());
						LOG.debug("Cookie expiration = -1, setting to 10 years");
					} else {
						defaultHandler.parse(cookie, value);
					}
				}

				@Override
				public boolean match(Cookie cookie, CookieOrigin origin) {
					return defaultHandler.match(cookie, origin);
				}
			});
		}
	}

	public DefaultWebUploadClient(PhotoFileResolver resolver) {
		cleaner = new HtmlCleaner();
		client = new DefaultHttpClient();
		client.setHttpRequestRetryHandler(new UnlimitedRetry());
		client.getParams().setBooleanParameter("http.protocol.handle-redirects", Boolean.FALSE);
		context = new BasicHttpContext();
		/**
		 * Add a custom cookie handler for "-1" expiry
		 */
		client.getCookieSpecs().register(MYSPEC, new CookieSpecFactory() {
			@Override
			public CookieSpec newInstance(HttpParams params) {
				return new MyCookieSpec();
			}
		});

		cookies = new FileCookieStore();
		context.setAttribute(ClientContext.COOKIE_STORE, cookies);
		CleanerProperties props = cleaner.getProperties();
		props.setAllowHtmlInsideAttributes(true);
		props.setAllowMultiWordAttributes(true);
		props.setRecognizeUnicodeChars(true);
		props.setAdvancedXmlEscape(true);
		props.setOmitComments(true);
		fileResolver = resolver;
	}

	// for testing
	public FileCookieStore getCookieStore() {
		return cookies;
	}

	// public void delete(int pageId) {
	//
	// //http://www.crazyguyonabike.com/?command=do_delete&page_id=173278&doc_id=&from=editcontents
	// HttpGet get = new HttpGet(createURI("doc/edit/page/", ))
	// }

	/**
	 * Attempts to get the current username from the "/my/account" page, returns
	 * null if not logged in.
	 * 
	 * @return
	 */
	private String doMyAccountGET() {
		try {
			HttpGet get = new HttpGet(createURI("/my/account/", null));
			HttpResponse result = client.execute(get, context);
			assertStatusCodeOrThrow(result.getStatusLine().getStatusCode(), HttpStatus.SC_OK,
					HttpStatus.SC_MOVED_PERMANENTLY);
			if (result.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				return EntityUtils.toString(result.getEntity());
			}
			result.getEntity().consumeContent(); // closes connection
			return null;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void logout(CompletionCallback<Void> callback) {
		asyncExec(new LogoutTask(callback));
		currentRealname = currentUsername = null;
	}

	class LogoutTask extends Task<Void> {

		public LogoutTask(CompletionCallback<Void> callback) {
			super(callback);
		}

		@Override
		protected Void doRun() throws Exception {
			doLogoutGET();
			return null;
		}
	}

	static class RequestBuilder {

		List<NameValuePair> d = new ArrayList<NameValuePair>();

		public void add(String name, String value) {
			d.add(new BasicNameValuePair(name, value));
		}

		public UrlEncodedFormEntity createFormEntity() {
			try {
				return new UrlEncodedFormEntity(d);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public class ServerOperationException extends RuntimeException implements HtmlProvider {
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

	@Override
	public void login(final String username, final String password, CompletionCallback<String> callback) {
		asyncExec(new LoginTask(callback, username, password));
	}

	class LoginTask extends Task<String> {

		final String username;
		final String password;

		public LoginTask(CompletionCallback<String> callback, String username, String password) {
			super(callback);
			this.username = username;
			this.password = password;
		}

		@Override
		protected String doRun() throws Exception {
			String myAccountHtml = doMyAccountGET();

			if (username == null && myAccountHtml == null) {
				// not logged in and no new username given
				throw new ServerOperationException("Failed to login with username '" + username + "'", "");
			}

			if (myAccountHtml != null) {
				// already logged in
				TagNode myAccount = cleaner.clean(myAccountHtml);
				String loggedInAs = CGOABHtmlUtils.getUsernameFromMyAccount(myAccount);
				if (username != null && username.equals(loggedInAs)) {
					// logged in as someone else, logout first before
					// logging back in
					doLogoutGET();
				} else {
					// logged in as expected user or no new username was
					// given
					currentUsername = loggedInAs;
					currentRealname = CGOABHtmlUtils.getRealnameFromMyAccount(myAccount);
					return loggedInAs;
				}
			}

			String loginHtml = doLoginPOST(username, password);

			// we could test post result for:
			//
			// Success: "Logging in xyz..." page, (META redirect)
			// Failure: "Password incorrect"
			//
			// to be more flexible just check /my/ directly

			myAccountHtml = doMyAccountGET();
			if (myAccountHtml == null) {
				// password/username incorrect
				throw new ServerOperationException("Failed to login with username '" + username + "'", loginHtml);
			}
			TagNode myAccount = cleaner.clean(myAccountHtml);
			currentUsername = CGOABHtmlUtils.getUsernameFromMyAccount(myAccount);
			assert currentUsername.equals(username);
			currentRealname = CGOABHtmlUtils.getRealnameFromMyAccount(myAccount);
			cookies.persist();

			return username;
		}
	}

	private static void setCookieSpec(HttpRequest request) {
		HttpClientParams.setCookiePolicy(request.getParams(), MYSPEC);
	}

	private void doLogoutGET() {
		HttpGet get = new HttpGet(createURI("/logout/", null));
		setCookieSpec(get); // right now expire=-1 is only set on logout
		HttpResponse result;
		try {
			result = client.execute(get, context);
			assertStatusCodeOrThrow(result.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
			result.getEntity().consumeContent();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String doLoginPOST(String username, String password) throws ClientProtocolException, IOException {
		// login
		HttpPost post = new HttpPost(createURI("/login/index.html", null));
		RequestBuilder rb = new RequestBuilder();
		rb.add("command", "login");
		rb.add("username", username);
		rb.add("password", password);
		rb.add("persistent", "0");
		rb.add("button", "Login");
		HttpResponse result;

		post.setEntity(rb.createFormEntity());
		result = client.execute(post, context);
		assertStatusCodeOrThrow(result.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
		return EntityUtils.toString(result.getEntity());
	}

	private static void assertStatusCodeOrThrow(int actual, int... expected) {
		for (int i : expected) {
			if (i == actual) {
				return;
			}
		}
		throw new IllegalStateException("Unexpected staus code '" + actual + "'");
	}

	private static URI createURI(String path, String query) {
		try {
			return URIUtils.createURI("http", CRAZYGUYONABIKE_URL, -1, path, query, null);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private TagNode doGetDocumentsGET() throws ClientProtocolException, IOException {
		HttpGet get = new HttpGet(createURI("/my/", null));
		HttpResponse response = client.execute(get, context);
		return cleaner.clean(new InputStreamReader(response.getEntity().getContent()));
	}

	@Override
	public void getDocuments(CompletionCallback<List<DocumentDescription>> callback) {
		asyncExec(new GetDocumentsTask(callback));
	}

	class GetDocumentsTask extends Task<List<DocumentDescription>> {

		public GetDocumentsTask(CompletionCallback<List<DocumentDescription>> callback) {
			super(callback);
		}

		@Override
		protected List<DocumentDescription> doRun() throws Exception {
			TagNode root = doGetDocumentsGET();
			Object[] table = root.evaluateXPath("/body/table[5]/tbody/tr/td/table/tbody/tr");

			List<DocumentDescription> journals = new ArrayList<DocumentDescription>();
			// tr[1] is table header, tr[2] onwards are journal listings...
			for (int i = 1; i < table.length; ++i) {
				journals.add(createJournal((TagNode) table[i]));
			}
			return journals;
		}
	}

	private static class PhotoUploadProgressAdapter implements PhotoUploadProgressListener {
		@Override
		public void uploadPhotoProgress(Photo photo, long bytes, long total) {
		}
	}

	private int doAddPagePOST(int journalID, Page page) throws IllegalStateException, IOException {
		HttpPost post = new HttpPost(createURI("/doc/edit/page/", null));
		RequestBuilder rb = new RequestBuilder();
		rb.add("toc_heading_size", page.getHeadingStyle().toString());
		rb.add("toc_heading_bold", toOnOff(page.isBold()));
		rb.add("toc_heading_italic", toOnOff(page.isItalic()));
		rb.add("toc_level", Integer.toString(page.getIndent()));
		rb.add("visible", toOnOff(page.isVisible()));
		rb.add("date", page.getDate().toString());
		rb.add("distance", Float.toString(page.getDistance()));
		rb.add("title", page.getTitle());
		rb.add("headline", page.getHeadline());
		rb.add("text", page.getText());
		rb.add("edit_comment", page.getEditComment());
		rb.add("format", page.getFormat().toString().toLowerCase());
		rb.add("submit", "Upload Pic or File");
		rb.add("command", "do_add");
		rb.add("submitted", "1");
		// TODO fix!!!!
		rb.add("sequence", "10000");
		rb.add("doctype", "journal");
		rb.add("from", "editcontents");
		rb.add("doc_id", Integer.toString(journalID));
		rb.add("update_timestamp", "off");

		post.setEntity(rb.createFormEntity());
		HttpResponse result = client.execute(post, context);
		int statusCode = result.getStatusLine().getStatusCode();
		if (isRedirect(statusCode)) {
			// page-id can be found in redirect header
			result.getEntity().consumeContent();
			Header locationHeader = result.getFirstHeader("Location");
			if (locationHeader == null) {
				throw new IllegalStateException("No location header returned by server.");
			}
			String location = locationHeader.getValue();
			Matcher match = PAGE_ID_PATTERN.matcher(location);
			if (!match.matches()) {
				throw new IllegalStateException("Could not find page_id in url '" + location + "'");
			}
			return Integer.parseInt(match.group(1));
		} else if (statusCode == HttpStatus.SC_OK) {
			// the page contains the error message
			throw new ServerOperationException("Failed to add page", EntityUtils.toString(result.getEntity()));
		} else {
			throw new IllegalStateException("Unexpected status code '" + statusCode + "'");
		}
	}

	@Override
	public void addPhoto(final int pageId, final Photo photo, CompletionCallback<Void> callback,
			PhotoUploadProgressListener progressListener) {
		final PhotoUploadProgressListener listener = progressListener == null ? new PhotoUploadProgressAdapter()
				: progressListener;
		asyncExec(new AddPhotoTask(callback, pageId, photo, fileResolver, listener));
	}

	@Override
	public void createNewPage(final int documentId, final Page page, CompletionCallback<Integer> callback) {
		asyncExec(new AddPageTask(callback, documentId, page));
	}

	class AddPageTask extends Task<Integer> {

		private int documentId;
		private Page page;

		public AddPageTask(CompletionCallback<Integer> callback, int documentId, Page page) {
			super(callback);
			this.documentId = documentId;
			this.page = page;
		}

		@Override
		protected Integer doRun() throws Exception {
			return doAddPagePOST(documentId, page);
		}
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

	private static String toOnOff(boolean value) {
		return value ? "on" : "off";
	}

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

	private int extractDocIdFromURL(String url) {
		Matcher match = DOC_ID_PATTERN.matcher(url);
		if (!match.matches()) {
			throw new IllegalArgumentException("Could not find doc_id in url '" + url + "'");
		}
		return Integer.parseInt(match.group(1));
	}

	private static String clean(String s) {
		s = s.replace("&nbsp;", " ");
		// s = StringEscapeUtils.unescapeHtml(s);
		return s.trim();
	}

	private DocumentDescription createJournal(TagNode node) throws XPatherException {
		Object[] e = node.evaluateXPath("td");
		String typeString = clean(((TagNode) e[0]).getText().toString()).toUpperCase();
		TagNode anchorNode = ((TagNode) e[1]).getElementsByName("a", true)[0];
		String link = anchorNode.getAttributeByName("href");
		int id = extractDocIdFromURL(link);
		String title = clean(anchorNode.getText().toString());
		String status = clean(((TagNode) e[3]).getText().toString());
		String rawCount = clean(((TagNode) e[4]).getText().toString());
		rawCount = rawCount.replace(",", "");
		int hits = Integer.parseInt(rawCount);
		return new DocumentDescription(title, hits, status, id, DocumentType.valueOf(typeString));
	}

	void toFile(String name, HttpEntity entity) throws IOException {
		FileWriter file = new FileWriter(name);
		file.write(EntityUtils.toString(entity));
		file.close();
	}

	@Override
	public String getCurrentUsername() {
		return currentUsername;
	}

	@Override
	public String getCurrentUserRealName() {
		return currentRealname;
	}

	class AddPhotoTask extends Task<Void> {

		private PhotoUploadProgressListener listener;
		private Photo photo;
		private int pageId;
		private HttpPost post;
		private PhotoFileResolver resolver;

		public AddPhotoTask(CompletionCallback<Void> callback, int pageId, Photo photo,
				PhotoFileResolver photoResolver, PhotoUploadProgressListener listener) {
			super(callback);
			this.listener = listener;
			this.photo = photo;
			this.resolver = photoResolver;
			this.pageId = pageId;
		}

		@Override
		public Void doRun() throws ClientProtocolException, IOException {
			post = new HttpPost(createURI("doc/edit/page/pic/", null));
			File file = photo.getFile();
			if (resolver != null) {
				File resolved = resolver.getFileFor(photo);
				if (resolved != null) {
					file = resolved;
				}
			}
			FileBody fb = new ProgressTrackingFileBody(file, new ProgressListener() {
				@Override
				public void transferred(long sent, long total) {
					listener.uploadPhotoProgress(photo, sent, total);
				}
			});
			MultipartEntity request = new MultipartEntity();
			request.addPart("upload_filename", fb);
			request.addPart("filename", new StringBody(""));
			// new StringBody(StringUtils.nullToEmpty(photo.getImageName())));
			request.addPart("caption", new StringBody(StringUtils.nullToEmpty(photo.getCaption())));
			request.addPart("rotate_degrees", new StringBody("auto"));
			request.addPart("include_in_random", new StringBody("on"));
			request.addPart("button", new StringBody("finish"));
			request.addPart("command", new StringBody("do_add"));
			request.addPart("submitted", new StringBody("1"));
			request.addPart("doctype", new StringBody("journal"));
			request.addPart("from", new StringBody("editcontents-editpage"));
			request.addPart("page_id", new StringBody(Integer.toString(pageId)));
			request.addPart("update_timestamp", new StringBody("on"));
			post.setEntity(request);

			HttpResponse response = client.execute(post, context);

			int statusCode = response.getStatusLine().getStatusCode();
			if (isRedirect(statusCode)) {
				// ok, expected
				// TODO assert is a redirect to /doc/edit/page/?...
				response.getEntity().consumeContent();
				return null;
			} else if (statusCode == HttpStatus.SC_OK) {
				String html = EntityUtils.toString(response.getEntity());
				throw new ServerOperationException("Error adding photo " + photo.getFile(), html);
			} else {
				throw new IllegalStateException("Unexpected status code '" + statusCode + "'");
			}

		}

		@Override
		public void cancel() {
			// TODO synch?
			if (post != null) {
				post.abort();
			}
		}
	}

	@Override
	public void dispose() {
		// don't bother to logout, just null data and kill worker
		currentRealname = currentUsername = null;
		super.dispose();
	}

	private class UnlimitedRetry extends DefaultHttpRequestRetryHandler {

		public UnlimitedRetry() {
			super(Integer.MAX_VALUE, false);
		}

		@Override
		public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
			// notify current operation
			fireOnRetry(exception, executionCount);
			return super.retryRequest(exception, executionCount, context);
		}
	}
}