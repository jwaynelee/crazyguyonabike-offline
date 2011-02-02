package com.cgoab.offline.util;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateChecker {

	private static Logger LOGGER = LoggerFactory.getLogger(UpdateChecker.class);

	private static final String ATOM_NS = "http://www.w3.org/2005/Atom";

	private static final String DOWNLOAD_FEED = "http://code.google.com/feeds/p/crazyguyonabike-offline/downloads/basic/";

	/* {name}.major.minor.patch-{os}.{extension} */
	private static final Pattern VERSION_PATTERN = Pattern.compile(".*\\.(\\d\\.\\d\\.\\d)-.*");

	/**
	 * Starts a new thread to get the latest version, and pops up a dialog box
	 * if a newer version is found.
	 * 
	 * @param display
	 */
	public static void checkForLatestVersion(final Display display) {
		// TODO add yes/no preference to check for updates
		// TODO pop up progress box when checking
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				final Version latestVersion = getLatestVersion();
				final Version currentVersion = getCurrentVersion();
				if (currentVersion == null || currentVersion.isLessThan(latestVersion)) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							String name = Utils.getImplementationTitleString(UpdateChecker.class);
							MessageDialog.openInformation(null, "New version of " + name + " found", "Version "
									+ latestVersion + " of " + name + " is now available. You are using version "
									+ currentVersion + ". It is recomended that you upgrade.");
						}
					});
				}
			}
		});
		thread.setDaemon(true);
		thread.start();
	}

	private static Version getCurrentVersion() {
		String versionString = Utils.getSpecificationVersion(UpdateChecker.class);
		return versionString == null ? null : Version.parse(versionString);
	}

	static Version findLatestVersion(Document xml) {
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
	 * Consults the google-code website for the latest version.
	 * 
	 * @return
	 */
	public static Version getLatestVersion() {
		InputStream stream = null;
		try {
			LOGGER.debug("Checking for latest version");
			URLConnection connection = new URL(DOWNLOAD_FEED).openConnection();
			Builder builder = new Builder();
			stream = connection.getInputStream();
			return findLatestVersion(builder.build(stream));
		} catch (Exception e) {
			/* TODO filter out failure to connect (probably offline) */
			LOGGER.error("Exception checking for latest version", e);
		} finally {
			Utils.close(stream);
		}
		return null;
	}

	public static void main(String[] args) {
		System.out.println(getLatestVersion());
	}
}
