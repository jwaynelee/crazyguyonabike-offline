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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.client.UploadClientFactory;
import com.cgoab.offline.client.web.DefaultWebUploadClientFactory;
import com.cgoab.offline.client.web.FileCookieStore;
import com.cgoab.offline.ui.MainWindow;
import com.cgoab.offline.ui.PreferenceUtils;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.ui.thumbnailviewer.ResizeStrategy;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.ui.util.UIExecutor;
import com.cgoab.offline.util.LatestVersionChecker;
import com.cgoab.offline.util.Utils;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;

/**
 * Creates and opens the application UI.
 */
public class Application implements Runnable {
	private static final String COOKIES_FILE = "cookies";
	public static final String CRAZYGUYONABIKE_HOST = "www.crazyguyonabike.com";
	public static final int CRAZYGUYONABIKE_PORT = -1;
	private static final Logger LOG = LoggerFactory.getLogger(Application.class);
	private static final String LOG_FILE = "log.properties";
	private static final String RESIZED_FOLDER_EXTENSION = ".resized";
	public static final File SETTINGS_DIR = new File(System.getProperty("user.home") + File.separator + ".cgoaboffline"
			+ File.separator);
	public static final ResizeStrategy THUMBNAIL_RESIZER = ThumbnailViewer.RESIZE_STRATEGY;
	private static final String THUMNAILS_FOLDER_EXTENSION = ".thumbnails";

	/**
	 * Assembles a new application with default settings.
	 */
	public static Application defaultApplication() {
		Application app = new Application();

		/* logging */
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

		PreferenceUtils.init(SETTINGS_DIR + File.separator + "preferences");
		display.addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {
				/* save preferences on exit */
				PreferenceUtils.save();
			}
		});
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
		app.setThumbnailFactory(new CachingThumbnailProviderFactory(display, THUMBNAIL_RESIZER,
				THUMNAILS_FOLDER_EXTENSION));
		app.setResizerFactory(new ImageMagickResizerServiceFactory(display, RESIZED_FOLDER_EXTENSION));

		return app;
	}

	public static void main(String[] args) {
		try {
			defaultApplication().run();
		} catch (Throwable e) {
			/* TODO open Swing MessageBox? else we fail silently */
			e.printStackTrace();
		}
	}

	private boolean configuredLogging;

	private Display display;

	private ImageMagickResizerServiceFactory resizerFactory;

	private CachingThumbnailProviderFactory thumbnailFactory;

	private UploadClientFactory uploadFactory;

	private void initLogging() {
		if (!configuredLogging) {
			BasicConfigurator.configure();
		}

		/* install JFace ILogger bridge */
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

	@Override
	public void run() {
		initLogging();
		try {
			/* check for a new version first */
			LatestVersionChecker.autoCheckForNewerVersion(display);

			String name = Utils.getImplementationTitleString(Application.class);
			String version = Utils.getImplementationVersion(Application.class);
			name = name == null ? "?" : name;
			version = version == null ? "?" : version;
			MainWindow app = new MainWindow(name + ":" + version);
			
			app.setThumbnailProviderFactory(thumbnailFactory);
			app.setResizerServiceFactory(resizerFactory);
			app.setUploadFactory(uploadFactory);
			app.open();
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
			display.dispose();
		}
	}

	public void setDisplay(Display display) {
		this.display = display;
	}

	public void setLogConfiguration(Properties logConfiguration) {
		/* configure logging ASAP */
		if (configuredLogging) {
			LOG.warn("Logging already configured!");
		}
		PropertyConfigurator.configure(logConfiguration);
		configuredLogging = true;
	}

	public void setResizerFactory(ImageMagickResizerServiceFactory resizerFactory) {
		this.resizerFactory = resizerFactory;
	}

	public void setThumbnailFactory(CachingThumbnailProviderFactory thumbnailFactory) {
		this.thumbnailFactory = thumbnailFactory;
	}

	private void setUploadFactory(UploadClientFactory uploadFactory) {
		this.uploadFactory = uploadFactory;
	}
}