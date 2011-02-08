package com.cgoab.offline.ui;

import java.io.File;
import java.io.IOException;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.util.Assert;
import com.cgoab.offline.util.Utils;

public class PreferenceUtils {

	private static final String PREFIX = "com.cgoab.";

	private static final Logger LOG = LoggerFactory.getLogger(PreferenceUtils.class);

	private static PreferenceStore preferenceStore;

	public static final String LAST_JOURNAL = PREFIX + "LAST_JOURNAL";

	public static final String USE_MOCK_CLIENT = PREFIX + "USE_MOCK_CLIENT";

	public static final String RESIZE_DIMENSIONS = PREFIX + "RESIZE_DIMENSION";

	public static final String MAGICK_PATH = PREFIX + "MAGICK_PATH";

	public static final String RESIZE_QUALITY = PREFIX + "RESIZE_QUALITY";

	public static final String NEW_PAGES_VISIBLE = PREFIX + "NEW_PAGES_VISIBLE";

	public static final String FONT = PREFIX + "FONT";

	public static final String CHECK_FOR_UPDATES = PREFIX + "CHECK_FOR_UPDATES";

	public static final IPreferenceStore getStore() {
		Assert.notNull(preferenceStore);
		return preferenceStore;
	}

	public static final void dispose() {
		preferenceStore = null;
	}

	public static final void init() {
		Assert.isNull(preferenceStore);
		preferenceStore = new PreferenceStore();
	}

	public static final void init(String preferencesPath) {
		Assert.isNull(preferenceStore);
		Assert.notNull(preferencesPath);
		File preferencesFile = new File(preferencesPath);
		/* create if needed */
		try {
			Utils.createFile(preferencesFile);
		} catch (IOException e) {
			LOG.warn("Failed to create preferences file: {}", e.getMessage());
		}
		preferenceStore = new PreferenceStore(preferencesPath);
		load();
	}

	public static final void save() {
		Assert.notNull(preferenceStore);
		try {
			preferenceStore.save();
		} catch (IOException e) {
			LOG.warn("Failed to save preferences");
		}
	}

	public static final void load() {
		Assert.notNull(preferenceStore);
		try {
			preferenceStore.load();
		} catch (IOException e) {
			LOG.warn("Failed to load preferences", e);
		}
	}
}