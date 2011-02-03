package com.cgoab.offline.util;

import static com.cgoab.offline.ui.PreferenceUtils.CHECK_FOR_UPDATES;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.ui.PreferenceUtils;

public class LatestVersionChecker {

	private static Logger LOGGER = LoggerFactory.getLogger(LatestVersionChecker.class);

	private static final String ATOM_NS = "http://www.w3.org/2005/Atom";

	private static final String DOWNLOAD_FEED_URL = "http://code.google.com/feeds/p/crazyguyonabike-offline/downloads/basic/";

	/* {name}.major.minor.patch-{os}.{extension} */
	private static final Pattern VERSION_PATTERN = Pattern.compile(".*\\.(\\d\\.\\d\\.\\d)-.*");

	/**
	 * Checks for a new version, blocking UI with progress bar then pops up a
	 * dialog box if a newer version is found.
	 * 
	 * @param display
	 */
	public static Version blockForLatestVersion(final Display display) {
		final AtomicReference<Version> latestVersion = new AtomicReference<Version>();
		IRunnableWithProgress progress = new IRunnableWithProgress() {
			@Override
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				monitor.beginTask("Checking for newer version", IProgressMonitor.UNKNOWN);
				latestVersion.set(doGetLatestVersion());
				monitor.done();
			}
		};
		ProgressMonitorDialog dialog = new ProgressMonitorDialog(null);
		try {
			dialog.run(true, true, progress);
		} catch (InvocationTargetException e) {
			/* ignore */
		} catch (InterruptedException e) {
			/* ignore */
		}

		/* cancelled dialog */
		if (dialog.getReturnCode() == Window.CANCEL) {
			return null;
		}
		return latestVersion.get();
	}

	/**
	 * Performs a check for a newer version (unless explicity disabled).
	 * 
	 * @param display
	 */
	public static void autoCheckForNewerVersion(Display display) {
		final IPreferenceStore store = PreferenceUtils.getStore();

		/* default to true if unset */
		if (!store.contains(CHECK_FOR_UPDATES)) {
			store.setValue(CHECK_FOR_UPDATES, true);
		}

		if (!store.getBoolean(CHECK_FOR_UPDATES)) {
			return;
		}

		final Version latest = blockForLatestVersion(display);
		if (latest == null) {
			return;
		}

		final Version currentVersion = currentVersion();
		if (currentVersion == null || currentVersion.isLessThan(latest)) {
			MessageDialogWithToggle toggle = MessageDialogWithToggle.openInformation(null, "New version found",
					"Version " + latest + " is available. You are using version " + currentVersion
							+ ".\n\nIt is recomended that you upgrade.", "always check for updates?", true, null, null);
			/* manually set, else "always" | "never" stored in preferences */
			store.setValue(CHECK_FOR_UPDATES, toggle.getToggleState());

		}
	}

	public static Version currentVersion() {
		String versionString = Utils.getSpecificationVersion(LatestVersionChecker.class);
		return versionString == null ? null : Version.parse(versionString);
	}

	static Version extractLatestVersion(Document xml) {
		Elements elements = xml.getRootElement().getChildElements("entry", ATOM_NS);
		for (int i = 0; i < elements.size(); ++i) {
			Element element = elements.get(i);
			String id = element.getFirstChildElement("id", ATOM_NS).getValue();
			Matcher matcher = VERSION_PATTERN.matcher(id);
			if (matcher.matches()) {
				Version version = Version.parse(matcher.group(1));
				LOGGER.debug("Latest version is {}", version);
				return version;
			} else {
				/* error */
				LOGGER.info("Failed to detect version in download ID []", id);
			}
		}
		LOGGER.debug("Failed to find latest version");
		return null;
	}

	/**
	 * Queries website for latest version.
	 * 
	 * @return
	 */
	private static Version doGetLatestVersion() {
		InputStream stream = null;
		try {
			LOGGER.debug("Checking for latest version");
			URLConnection connection = new URL(DOWNLOAD_FEED_URL).openConnection();
			Builder builder = new Builder();
			stream = connection.getInputStream();
			return extractLatestVersion(builder.build(stream));
		} catch (Exception e) {
			/* TODO filter out failure to connect (probably offline) */
			LOGGER.error("Exception checking for latest version", e);
		} finally {
			Utils.close(stream);
		}
		return null;
	}

	public static void main(String[] args) {
		System.out.println(doGetLatestVersion());
	}
}
