package testutils.fakeserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.XPatherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import testutils.fakeserver.ServerModel.ServerJournal;
import testutils.fakeserver.ServerModel.ServerPage;
import testutils.fakeserver.ServerModel.ServerPhoto;

import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.web.CGOABHtmlUtils;

public class FakeCGOABServer {

	private static final Logger LOGGER = LoggerFactory.getLogger(FakeCGOABServer.class);

	private BasicHttpServer server;

	public static void main(String[] args) throws Exception {
		// runs CGOAB server with a UI
		FakeCGOABServer server = new FakeCGOABServer(8080);
		FakeServerUI ui = new FakeServerUI();
		ui.open(server);
		server.shutdown();
	}

	ServerModel model;

	public ServerModel getModel() {
		return model;
	}

	public FakeCGOABServer(int port) throws IOException, XPatherException {
		server = new BasicHttpServer(port, createRegistry());
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				server.loop();
			}
		});
		thread.start();
		model = new ServerModel();
		// populate journals from file
		List<DocumentDescription> documents = CGOABHtmlUtils.getDocuments(new HtmlCleaner().clean(getClass()
				.getResourceAsStream("MyPage.htm")));
		for (DocumentDescription doc : documents) {
			model.addJournal(new ServerJournal(doc.getTitle(), doc.getDocumentId()));
		}
	}

	public void shutdown() {
		server.shutdown();
	}

	public HttpRequestHandlerRegistry createRegistry() {
		HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
		registry.register("/login/index.html", new DoLoginHandler());
		registry.register("/logout/", new LogoutHandler());
		registry.register("/my/account/", new MyAccountHandler());
		registry.register("/my/", new MyHandler());
		registry.register("/doc/edit/page/", new EditPageHandler());
		registry.register("/doc/edit/page/pic/", new AddPhotoHandler());
		return registry;
	}

	class LogoutHandler extends BaseHandler {
		@Override
		protected void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException {
			response.setStatusCode(HttpStatus.SC_OK);
		}
	}

	abstract class BaseHandler implements HttpRequestHandler {
		@Override
		public final void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException {
			LOGGER.info(getClass().getSimpleName());
			doHandle(request, response, context);
		}

		protected abstract void doHandle(HttpRequest request, HttpResponse response, HttpContext context)
				throws HttpException, IOException;
	}

	class AddPhotoHandler extends BaseHandler {
		@Override
		public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException {

			final HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
			Wrapper wrapper = new Wrapper(entity);
			DiskFileItemFactory factory = new DiskFileItemFactory();
			FileUpload upload = new FileUpload(factory);
			try {
				upload.setProgressListener(new ProgressListener() {
					@Override
					public void update(long pBytesRead, long pContentLength, int pItems) {
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
						}
						LOGGER.info("Read " + pBytesRead + " (" + pItems + ")");
						/* fail the upload after 60% */
						if (((float) pBytesRead / pContentLength) > 0.6)
							throw new IllegalStateException("");
					}
				});
				List<FileItem> items = upload.parseRequest(wrapper);

				ServerPhoto photo = new ServerPhoto();
				int pageId = 0;
				for (FileItem fileItem : items) {
					if (fileItem instanceof DiskFileItem) {
						if (fileItem.isFormField()) {
							if (fileItem.getFieldName().equals("page_id")) {
								pageId = Integer.parseInt(fileItem.getString());
							}
							System.out.println(fileItem.getFieldName() + " - " + fileItem.getString());
						} else {
							DiskFileItem item = (DiskFileItem) fileItem;
							photo.setFilename(item.getName());
							photo.setSize((int) item.getSize());
						}
					}
				}
				ServerPage page = model.getPage(pageId);
				if (page != null) {
					page.addPhoto(photo);
				}
			} catch (FileUploadException e) {
				e.printStackTrace();
			}
			// expect redirect
			response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
		}
	}

	static class Wrapper implements RequestContext {
		private HttpEntity entity;

		public Wrapper(HttpEntity entity) {
			this.entity = entity;
		}

		@Override
		public String getCharacterEncoding() {
			return "";
		}

		@Override
		public int getContentLength() {
			return (int) entity.getContentLength();
		}

		public String getContentType() {
			return entity.getContentType().getValue();
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return entity.getContent();
		}
	}

	class EditPageHandler extends BaseHandler {
		@Override
		public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException {
			Map<String, String> params = parse(request);
			ServerPage page = new ServerPage();
			page.setHeadline(params.get("headline"));
			page.setTitle(params.get("title"));
			page.setText(params.get("text"));
			ServerJournal journal = model.getJournal(Integer.parseInt(params.get("doc_id")));
			int pageId = journal.addPage(page);
			response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
			response.addHeader(new BasicHeader("Location", "/doc/?page_id=" + pageId));
		}
	}

	private URL getResource(String resource) {
		return getClass().getResource(resource);
	}

	void copy(URL url, OutputStream out) {
		InputStream fis = null;
		try {
			fis = url.openStream();
			byte[] buff = new byte[8 * 1024];
			int bytes;
			while ((bytes = fis.read(buff)) > 0) {
				out.write(buff, 0, bytes);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
				}
			}
		}
	}

	boolean isLoggedIn(HttpRequest request) {
		Header cookie = request.getFirstHeader("Cookie");
		if (cookie == null) {
			return false;
		}

		HeaderElement[] elements = cookie.getElements();
		String id = null;
		for (int i = 0; i < elements.length; ++i) {
			if (elements[i].getName().equals("id")) {
				id = elements[i].getValue().trim();
			}
		}
		return id != null && model.isValidUserId(id);
	}

	class MyAccountHandler extends BaseHandler {
		@Override
		public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException {

			/* check for valid cookie */
			if (isLoggedIn(request)) {
				EntityTemplate body = new EntityTemplate(new ContentProducer() {
					@Override
					public void writeTo(OutputStream outstream) throws IOException {
						copy(getResource("MyAccountPage.htm"), outstream);
					}
				});
				body.setContentType("text/html; charset=UTF-8");
				response.setEntity(body);
				response.setStatusCode(HttpStatus.SC_OK);
				return;
			} else {
				// reidrect
				response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
			}
		}
	}

	class MyHandler extends BaseHandler {
		@Override
		public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException {
			if (isLoggedIn(request)) {
				EntityTemplate body = new EntityTemplate(new ContentProducer() {
					@Override
					public void writeTo(OutputStream outstream) throws IOException {
						copy(getResource("MyPage.htm"), outstream);
					}
				});
				body.setContentType("text/html; charset=UTF-8");
				response.setEntity(body);
				response.setStatusCode(HttpStatus.SC_OK);
			} else {
				// reidrect
				response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
			}
		}
	}

	class StaticHandler extends BaseHandler {
		URL url;

		public StaticHandler(URL url) {
			this.url = url;
		}

		@Override
		public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException {
			EntityTemplate body = new EntityTemplate(new ContentProducer() {
				@Override
				public void writeTo(OutputStream outstream) throws IOException {
					copy(url, outstream);
				}
			});
			body.setContentType("text/html; charset=UTF-8");
			response.setEntity(body);
			response.setStatusCode(HttpStatus.SC_OK);
		}
	}

	static Map<String, String> parse(HttpRequest request) throws IOException {
		if (!(request instanceof HttpEntityEnclosingRequest)) {
			return Collections.emptyMap();
		}
		List<NameValuePair> params = URLEncodedUtils.parse(((HttpEntityEnclosingRequest) request).getEntity());
		Map<String, String> map = new HashMap<String, String>(params.size());
		for (NameValuePair param : params) {
			map.put(param.getName(), param.getValue());
		}
		return map;
	}

	class DoLoginHandler extends BaseHandler {

		@Override
		public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException {
			if (!request.getRequestLine().getMethod().toUpperCase().equals("POST")) {
				throw new UnsupportedOperationException();
			}
			// expect form entity
			Map<String, String> params = parse(request);
			String username = params.get("username");
			String id = model.loggedIn(username);
			LOGGER.info("Logged in as {}; id={}", username, id);
			response.addHeader("Set-Cookie", "id=" + id + "; path=/; domain=localhost");
			response.setStatusCode(HttpStatus.SC_OK);
		}
	}
}