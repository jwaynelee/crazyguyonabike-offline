package com.cgoab.offline;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.ui.PageEditor;
import com.cgoab.offline.ui.Preferences;
import com.cgoab.offline.ui.thumbnailviewer.FitWithinResizeStrategy;
import com.cgoab.offline.ui.thumbnailviewer.ResizeStrategy;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProviderFactory;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.util.resizer.ResizerServiceFactory;

public class Application implements Runnable {
	public static final File SETTINGS_DIR = new File(System.getProperty("user.home") + File.separator + ".cgoaboffline"
			+ File.separator);
	public static final ResizeStrategy THUMBNAIL_RESIZER = new FitWithinResizeStrategy(new Point(
			ThumbnailViewer.THUMBNAIL_WIDTH, ThumbnailViewer.THUMBNAIL_HEIGHT));
	private static final String LOG_FILE = "log.properties";
	private static Logger LOG = LoggerFactory.getLogger(Application.class);
	private Preferences preferences;
	private ThumbnailProviderFactory thumbnailFactory;
	private ResizerServiceFactory resizerFactory;
	private Display display;
	private boolean configuredLogging;

	public void setThumbnailFactory(ThumbnailProviderFactory thumbnailFactory) {
		this.thumbnailFactory = thumbnailFactory;
	}

	public void setResizerFactory(ResizerServiceFactory resizerFactory) {
		this.resizerFactory = resizerFactory;
	}

	/**
	 * Constructs a new application with default values.
	 * 
	 * @return
	 */
	public static Application defaultApplication() {
		Application app = new Application();
		InputStream logConfigStream = Application.class.getResourceAsStream(LOG_FILE);
		if (logConfigStream != null) {
			Properties config = new Properties();
			try {
				config.load(logConfigStream);
				app.setLogConfiguration(config);
			} catch (IOException e) {
				/* TODO fail? */
				e.printStackTrace();
			}
		}
		Display display = new Display();
		app.setDisplay(display);

		app.setThumbnailFactory(new ThumbnailProviderFactory(display, THUMBNAIL_RESIZER));
		app.setResizerFactory(new ResizerServiceFactory(display));

		Preferences preferences = new Preferences(new File(SETTINGS_DIR + File.separator + "preferences"));
		app.setPreferences(preferences);
		return app;
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

	private PageEditor createEditor() {
		PageEditor editor = new PageEditor(new Shell(display));
		editor.setThumbnailProviderFactory(thumbnailFactory);
		editor.setResizerServiceFactory(resizerFactory);
		editor.setPreferences(preferences);
		return editor;
	}

	public void run() {
		startLogging();
		try {
			PageEditor editor = createEditor();
			editor.open();
		} catch (Throwable t) {
			LOG.error("Unhandled exception, application will terminate", t);
		} finally {
			if (resizerFactory != null) {
				LOG.info("Dispoising resizer factory");
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

	private void startLogging() {
		if (!configuredLogging) {
			BasicConfigurator.configure();
		}
	}

	public static void main(String[] args) {
		defaultApplication().run();
	}
}