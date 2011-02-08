package testutils.fakeserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple standalone server used for testing.
 */
public class BasicHttpServer {

	private static Logger LOGGER = LoggerFactory.getLogger(BasicHttpServer.class);

	private final HttpService httpService;
	private final HttpParams params;
	private final ServerSocket serversocket;

	public BasicHttpServer(int port, HttpRequestHandlerRegistry reqistry) throws IOException {
		this.serversocket = new ServerSocket(port);
		this.params = new BasicHttpParams();
		this.params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 2000)
				.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 4 * 1024)
				.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
				.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
				.setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1");

		// Set up the HTTP protocol processor
		BasicHttpProcessor httpproc = new BasicHttpProcessor();
		httpproc.addInterceptor(new ResponseDate());
		httpproc.addInterceptor(new ResponseServer());
		httpproc.addInterceptor(new ResponseContent());
		httpproc.addInterceptor(new ResponseConnControl());

		// Set up request handlers

		// Set up the HTTP service
		this.httpService = new HttpService(httpproc, new DefaultConnectionReuseStrategy(),
				new DefaultHttpResponseFactory());
		this.httpService.setParams(this.params);
		this.httpService.setHandlerResolver(reqistry);
	}

	public int getLocalPort() {
		return serversocket.getLocalPort();
	}

	public void shutdown() {
		try {
			serversocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Enters the accept loop, terminates when Thread is interrupted
	 */
	public void loop() {
		LOGGER.info("Server listening on port {}", serversocket.getLocalPort());
		while (true) {
			try {
				// Set up HTTP connection
				Socket socket = this.serversocket.accept();
				DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
				LOGGER.info("Accepted connection from {}", socket.getInetAddress());
				conn.bind(socket, this.params);
				new ServerTask(this.httpService, conn).run();
			} catch (SocketException ex) {
				return;
			} catch (IOException e) {
				LOGGER.warn("Error setting up connection", e);
				return;
			}
		}
	}

	static class ServerTask implements Runnable {

		private final HttpServerConnection conn;
		private final HttpService httpservice;

		public ServerTask(final HttpService httpservice, final HttpServerConnection conn) {
			this.httpservice = httpservice;
			this.conn = conn;
		}

		public void run() {
			HttpContext context = new BasicHttpContext(null);
			try {
				while (this.conn.isOpen()) {
					this.httpservice.handleRequest(this.conn, context);
				}
			} catch (ConnectionClosedException ex) {
				LOGGER.warn("Client closed connection", ex);
			} catch (SocketTimeoutException e) {
				/* ignore */
				LOGGER.warn("Socket timeout, closing");
			} catch (IOException ex) {
				LOGGER.warn("I/O error", ex);
			} catch (HttpException ex) {
				LOGGER.warn("Unrecoverable HTTP protocol violation", ex);
			} catch (Throwable ex) {
				LOGGER.warn("Unexpected exception", ex);
			} finally {
				try {
					this.conn.shutdown();
				} catch (IOException ignore) {
				}
			}
		}

	}
}