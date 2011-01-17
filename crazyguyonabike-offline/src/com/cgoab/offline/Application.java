package com.cgoab.offline;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.util.ILogger;
import org.eclipse.jface.util.Policy;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.client.UploadClientFactory;
import com.cgoab.offline.client.web.DefaultWebUploadClientFactory;
import com.cgoab.offline.client.web.FileCookieStore;
import com.cgoab.offline.ui.ApplicationWindow;
import com.cgoab.offline.ui.Preferences;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.ui.thumbnailviewer.FitWithinResizeStrategy;
import com.cgoab.offline.ui.thumbnailviewer.ResizeStrategy;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.ui.util.UIExecutor;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;

public class Application implements Runnable {
	public static final File SETTINGS_DIR = new File(System.getProperty("user.home") + File.separator + ".cgoaboffline"
			+ File.separator);
	public static final ResizeStrategy THUMBNAIL_RESIZER = new FitWithinResizeStrategy(new Point(
			ThumbnailViewer.THUMBNAIL_WIDTH, ThumbnailViewer.THUMBNAIL_HEIGHT));
	private static final String LOG_FILE = "log.properties";
	private static final String THUMNAILS_FOLDER = ".thumbnails";
	private static final String RESIZED_FOLDER = ".resized";
	private static final String COOKIES_FILE = "cookies";
	public static final String CRAZYGUYONABIKE_HOST = "www.crazyguyonabike.com";
	public static final int CRAZYGUYONABIKE_PORT = -1;
	private static Logger LOG = LoggerFactory.getLogger(Application.class);
	private Preferences preferences;
	private CachingThumbnailProviderFactory thumbnailFactory;
	private ImageMagickResizerServiceFactory resizerFactory;
	private Display display;
	private boolean configuredLogging;
	private UploadClientFactory uploadFactory;

	public void setThumbnailFactory(CachingThumbnailProviderFactory thumbnailFactory) {
		this.thumbnailFactory = thumbnailFactory;
	}

	public void setResizerFactory(ImageMagickResizerServiceFactory resizerFactory) {
		this.resizerFactory = resizerFactory;
	}

	/**
	 * Constructs a new application with default settings.
	 * 
	 * @return
	 */
	public static Application defaultApplication() {
		Application app = new Application();
		InputStream logConfigStream = Application.class.getResourceAsStream(LOG_FILE);
		if (logConfigStream != null) {
			try {
				Properties config = new Properties();
				config.load(logConfigStream);
				app.setLogConfiguration(config);
			} catch (IOException e) {
				/* TODO fail? */
				e.printStackTrace();
			}
		}
		Display display = new Display();
		app.setDisplay(display);
		final FileCookieStore cookieStore = new FileCookieStore(SETTINGS_DIR + File.separator + COOKIES_FILE);
		display.addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {
				/* save cookies on exit */
				cookieStore.persist();
			}
		});
		DefaultWebUploadClientFactory uploadFactory = new DefaultWebUploadClientFactory();
		uploadFactory.setCookies(cookieStore);
		uploadFactory.setHost(System.getProperty("host", CRAZYGUYONABIKE_HOST));
		uploadFactory.setPort(Integer.getInteger("port", CRAZYGUYONABIKE_PORT));
		uploadFactory.setCallbackExecutor(new UIExecutor(display));
		app.setUploadFactory(uploadFactory);
		app.setThumbnailFactory(new CachingThumbnailProviderFactory(display, THUMBNAIL_RESIZER, THUMNAILS_FOLDER));
		app.setResizerFactory(new ImageMagickResizerServiceFactory(display, RESIZED_FOLDER));

		Preferences preferences = new Preferences(new File(SETTINGS_DIR + File.separator + "preferences"));
		app.setPreferences(preferences);
		return app;
	}

	private void setUploadFactory(UploadClientFactory uploadFactory) {
		this.uploadFactory = uploadFactory;
	}

	public void setLogConfiguration(Properties logConfiguration) {
		/* configure logging ASAP */
		if (configuredLogging) {
			LOG.warn("Logging already configured!");
		}
		PropertyConfigurator.configure(logConfiguration);
		configuredLogging = true;
	}

	public void setDisplay(Display display) {
		this.display = display;
	}

	public void setPreferences(Preferences preferences) {
		this.preferences = preferences;
	}

	public void run() {
		initLogging();
		Shell shell = null;
		try {
			shell = new Shell(display);
			ApplicationWindow editor = new ApplicationWindow(shell);
			editor.setThumbnailProviderFactory(thumbnailFactory);
			editor.setResizerServiceFactory(resizerFactory);
			editor.setPreferences(preferences);
			editor.setUploadFactory(uploadFactory);
			editor.open();
		} catch (Throwable t) {
			LOG.error("Unhandled exception, application will terminate", t);
		} finally {
			if (resizerFactory != null) {
				LOG.info("Disposing resizer factory");
				resizerFactory.dispose();
			}
			if (thumbnailFactory != null) {
				LOG.info("Disposing thumbnail factory");
				thumbnailFactory.dispose();
			}
			preferences.save();
			display.dispose();
		}
	}

	private void initLogging() {
		if (!configuredLogging) {
			BasicConfigurator.configure();
		}

		// install JFace ILogger bridge
		Policy.setLog(new ILogger() {
			@Override
			public void log(IStatus status) {
				switch (status.getSeverity()) {
				case IStatus.OK:
					LOG.debug(status.getMessage(), status.getException());
					break;
				case IStatus.INFO:
					LOG.info(status.getMessage(), status.getException());
					break;
				case IStatus.WARNING:
					LOG.warn(status.getMessage(), status.getException());
					break;
				case IStatus.CANCEL:
					LOG.info(status.getMessage(), status.getException());
					break;
				default: // IStatus.ERROR
					LOG.error(status.getMessage(), status.getException());
				}
			}
		});
	}

	public static void main(String[] args) {
		try {
			defaultApplication().run();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}