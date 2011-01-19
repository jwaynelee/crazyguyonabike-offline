package com.cgoab.offline.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Nodes;
import nu.xom.Serializer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Preferences {

	private static Log LOG = LogFactory.getLog(Preferences.class);

	public static final String USE_MOCK_IMPL_PATH = "/internal/useMockClient";

	private static String strip(String path) {
		if (path.startsWith("/")) {
			return path.substring(1);
		}
		return path;
	}

	private final File preferencesFile;

	private Document root = new Document(new Element("preferences"));

	public Preferences() {
		this(null);
	}

	public Preferences(File file) {
		preferencesFile = file;
		refresh();
	}

	private Element findElement(String path) {
		StringTokenizer t = new StringTokenizer(path, "/");
		Element n = root.getRootElement();
		while (t.hasMoreTokens()) {
			String step = t.nextToken();
			Element next = n.getFirstChildElement(step);
			if (next == null) {
				next = new Element(step);
				n.appendChild(next);
			}
			n = next;
		}
		return n;
	}

	public String getValue(String path) {
		Nodes result = root.getRootElement().query(strip(path));
		if (result.size() == 0) {
			return null;
		} else {
			Element n = (Element) result.get(0);
			Element value = n.getFirstChildElement("value");
			return value == null ? null : value.getValue();
		}
	}

	public List<String> getValues(String path) {
		Nodes result = root.getRootElement().query(strip(path));
		if (result.size() == 0) {
			return Collections.emptyList();
		}
		Element n = (Element) result.get(0);
		Elements values = n.getChildElements("value");
		List<String> r = new ArrayList<String>(values.size());
		for (int i = 0; i < values.size(); ++i) {
			r.add(values.get(i).getValue());
		}
		return r;
	}

	// reloads values from file
	public void refresh() {
		if (preferencesFile == null) {
			return;
		}
		LOG.debug("Refreshing preferences from [" + preferencesFile.getAbsolutePath() + "]");
		try {
			if (preferencesFile.exists()) {
				root = new Builder(false).build(preferencesFile);
			}
		} catch (Exception e) {
			LOG.warn("Failed to referesh preferences", e);
		}
	}

	public void removeValue(String path) {
		Nodes nodes = root.getRootElement().query(strip(path));
		if (nodes.size() == 0) {
			return;
		} else {
			nodes.get(0).detach();
		}
	}

	// flushes values to file
	public void save() {
		if (preferencesFile == null) {
			return;
		}
		FileOutputStream fos = null;
		LOG.debug("Saving preferences to [" + preferencesFile.getAbsolutePath() + "]");
		try {
			fos = new FileOutputStream(preferencesFile);
			Serializer serializer = new Serializer(fos);
			serializer.setIndent(2);
			serializer.write(root);
			serializer.flush();
		} catch (IOException e) {
			LOG.warn("Failed to save preferences", e);
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public void setValue(String path, String value) {
		Element e = findElement(path);
		e.removeChildren();
		Element ev = new Element("value");
		ev.appendChild(value);
		e.appendChild(ev);
	}

	public void setValues(String path, List<String> values) {
		Element e = findElement(path);
		e.removeChildren();
		for (String value : values) {
			Element ev = new Element("value");
			ev.appendChild(value);
			e.appendChild(ev);
		}
	}

}
