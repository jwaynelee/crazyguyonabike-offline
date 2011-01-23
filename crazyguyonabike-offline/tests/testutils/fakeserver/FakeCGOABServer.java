package testutils.fakeserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import testutils.fakeserver.FakeCGOABModel.ServerJournal;
import testutils.fakeserver.FakeCGOABModel.ServerPage;
import testutils.fakeserver.FakeCGOABModel.ServerPhoto;

import com.cgoab.offline.client.PhotoUploadProgressListener;
import com.cgoab.offline.util.Utils;

/**
 * A fake CGOAB for testing, backed by {@link FakeCGOABModel}.
 * 
 */
public class FakeCGOABServer {

	private static final Logger LOGGER = LoggerFactory.getLogger(FakeCGOABServer.class);

	private BasicHttpServer server;

	private final List<PhotoUploadProgressListener> photoListeners = new ArrayList<PhotoUploadProgressListener>();

	public void addPhotoUploadListener(PhotoUploadProgressListener listener) {
		photoListeners.add(listener);
	}

	public void removePhotoUploadListener(PhotoUploadProgressListener listener) {
		photoListeners.remove(listener);
	}

	public static void main(String[] args) throws Exception {
		// runs CGOAB server with a UI
		FakeCGOABServer server = new FakeCGOABServer(8080);
		FakeServerUI ui = new FakeServerUI();
		ui.open(server);
		server.shutdown();
	}

	FakeCGOABModel model;

	public FakeCGOABModel getModel() {
		return model;
	}

	public FakeCGOABServer(int port) throws Exception {
		server = new BasicHttpServer(port, createRegistry());
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				server.loop();
			}
		});
		thread.start();
		model = new FakeCGOABModel();
	}

	public BasicHttpServer getHttpServer() {
		return server;
	}

	public void shutdown() {
		server.shutdown();
	}

	public HttpRequestHandlerRegistry createRegistry() {
		HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
		registry.register("/login/index.html", new LoginHandler());
		registry.register("/logout/", new LogoutHandler());
		registry.register("/my/account/", new MyAccountHandler());
		registry.register("/my/", new MyHandler());
		registry.register("/doc/edit/page/", new PageHandler());
		registry.register("/doc/edit/page/pic/", new PicHandler());
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
			try {
				doHandle(request, response, context);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		protected abstract void doHandle(HttpRequest request, HttpResponse response, HttpContext context)
				throws Exception;
	}

	class PicHandler extends BaseHandler {
		@Override
		public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException {
			if (!isLoggedIn(request)) {
				respondWithHtml(response, "LoginPage.htm");
				return;
			}

			if ("GET".equals(method(request))) {
				respondWithHtml(response, "AddPic.htm");
			} else {
				final HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
				Wrapper wrapper = new Wrapper(entity);
				DiskFileItemFactory factory = new DiskFileItemFactory();
				FileUpload upload = new FileUpload(factory);
				try {
					upload.setProgressListener(new ProgressListener() {
						float lastLoggedPercentage = 0;

						@Override
						public void update(long pBytesRead, long pContentLength, int pItems) {
							float percentage = 100 * (float) pBytesRead / pContentLength;
							if (percentage - lastLoggedPercentage > 10) {
								lastLoggedPercentage = percentage;
								LOGGER.info("Read {} ({}%)", Utils.formatBytes(pBytesRead),
										String.format("%3.1f", percentage));
							}
							for (PhotoUploadProgressListener l : photoListeners) {
								l.uploadPhotoProgress(null, pBytesRead, pContentLength);
							}
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

	class PageHandler extends BaseHandler {
		@Override
		public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws Exception {
			if (!isLoggedIn(request)) {
				respondWithHtml(response, "LoginPage.htm");
			} else {
				Map<String, String> params = parse(request);
				if (method(request).equals("GET")) {
					String cmd = params.get("command");
					if ("do_delete".equals(cmd)) {
						int pageId = Integer.parseInt(params.get("page_id"));
						ServerPage page = model.getPage(pageId);
						page.getJournal().deletePage(page);
					} else {
						respondWithHtml(response, "EditPage.htm");
					}
				} else {
					ServerPage page = new ServerPage(params);
					ServerJournal journal = model.getJournal(Integer.parseInt(params.get("doc_id")));
					int pageId = journal.addPage(page);
					if (pageId == -1) {
						respondWithHtml(response, "ErrorPageOlderThanPrevious.htm");
					} else {
						response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
						response.addHeader(new BasicHeader("Location", "/doc/edit/page/pic/?page_id=" + pageId));
					}
				}
			}
		}
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
				respondWithHtml(response, "MyAccountPage.htm");
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
				respondWithHtml(response, "MyPage.htm");
			} else {
				// reidrect
				response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
			}
		}
	}

	// class StaticHandler extends BaseHandler {
	// String file;
	//
	// public StaticHandler(String file) {
	// this.file = file;
	// }
	//
	// @Override
	// public void doHandle(HttpRequest request, HttpResponse response,
	// HttpContext context) throws HttpException,
	// IOException {
	// respondWithHtml(response, file);
	// }
	// }

	static Map<String, String> parse(HttpRequest request) throws IOException, URISyntaxException {
		List<NameValuePair> params;
		if (!(request instanceof HttpEntityEnclosingRequest)) {
			params = URLEncodedUtils.parse(new URI(request.getRequestLine().getUri()), null);
		} else {
			params = URLEncodedUtils.parse(((HttpEntityEnclosingRequest) request).getEntity());
		}
		Map<String, String> map = new HashMap<String, String>(params.size());
		for (NameValuePair param : params) {
			map.put(param.getName(), param.getValue());
		}
		return map;
	}

	// private static void bind(InputStream in, OutputStream output, Map<String,
	// String> bindings) {
	// try {
	// BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	// String line;
	// while ((line = reader.readLine()) != null) {
	// for (Entry<String, String> e : bindings.entrySet()) {
	// line = line.replaceAll(".*\\${" + e.getKey() + "}.*", e.getValue());
	// }
	// output.write(line.getBytes());
	// }
	// } catch (IOException e) {
	// LOGGER.error("error copying", e);
	// } finally {
	// try {
	// in.close();
	// } catch (IOException e) {
	// }
	// }
	// }

	private static void respondWithHtml(HttpResponse response, String file) {
		final InputStream in = FakeCGOABServer.class.getResourceAsStream(file);
		EntityTemplate entity = new EntityTemplate(new ContentProducer() {
			@Override
			public void writeTo(OutputStream out) throws IOException {
				Utils.copy(in, out);
			}
		});
		entity.setContentType("text/html; charset=UTF-8");
		response.setEntity(entity);
		response.setStatusCode(HttpStatus.SC_OK);
	}

	private static String method(HttpRequest request) {
		return request.getRequestLine().getMethod().toUpperCase();
	}

	class LoginHandler extends BaseHandler {

		@Override
		public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
				IOException, URISyntaxException {
			if (method(request).equals("POST")) {
				// expect form entity
				Map<String, String> params = parse(request);
				String username = params.get("username");
				String password = params.get("password");
				String id = model.logIn(username, password);
				if (id != null) {
					LOGGER.info("Logged in as {}; id={}", username, id);
					response.addHeader("Set-Cookie", "id=" + id + "; path=/; domain=localhost");
					response.setStatusCode(HttpStatus.SC_OK);
					return;
				}
				LOGGER.error("Password/username not correct");
			}
			respondWithHtml(response, "LoginPage.htm");
		}
	}
}