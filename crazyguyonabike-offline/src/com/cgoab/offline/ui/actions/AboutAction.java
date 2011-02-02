package com.cgoab.offline.ui.actions;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.cgoab.offline.Application;
import com.cgoab.offline.util.NewFileAppender;
import com.cgoab.offline.util.StringUtils;
import com.cgoab.offline.util.Utils;

public class AboutAction extends Action {
	private Shell shell;

	public AboutAction(Shell shell) {
		super("About");
		this.shell = shell;
	}

	@Override
	public void run() {
		new AboutDialog(shell).open();
	}

	// public static void main(String[] args) {
	// for (Entry<?, ?> p : System.getProperties().entrySet()) {
	// System.out.println(p);
	// }
	// }

	public static void main(String[] args) {
		new AboutDialog(null).open();

	}

	public static class AboutDialog extends Dialog {

		private static final Name BUNDLE_VERSION = new Name("Bundle-Version");

		protected AboutDialog(Shell parentShell) {
			super(parentShell);
		}

		private void add(Composite parent, String name, String value) {
			new Label(parent, SWT.NONE).setText(name);
			new Text(parent, SWT.READ_ONLY | SWT.SINGLE).setText(value == null ? "<null>" : value);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			getShell().setText("About");
			Composite grid = new Composite(parent, SWT.NONE);
			grid.setLayout(new GridLayout(2, false));
			add(grid, "Name: ", Utils.getImplementationTitleString(AboutAction.class));
			add(grid, "Version: ", Utils.getImplementationVersion(AboutAction.class));
			add(grid, "Java version",
					System.getProperty("java.runtime.name") + " (" + System.getProperty("java.version") + ")");
			add(grid, "Java home: ", System.getProperty("java.home"));
			add(grid, "Log file: ", System.getProperty(NewFileAppender.FILE_PROPERTY));
			add(grid, "Settings dir: ", Application.SETTINGS_DIR.getAbsolutePath());

			GridData data = new GridData(GridData.FILL_HORIZONTAL);
			data.horizontalSpan = 2;
			new Label(grid, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData(data);
			new Label(grid, SWT.NONE).setText("Classpath:");

			Table table = new Table(grid, SWT.FULL_SELECTION | SWT.MULTI | SWT.BORDER);
			table.setLinesVisible(true);
			table.setHeaderVisible(true);
			data = new GridData(GridData.FILL_BOTH);
			data.heightHint = table.getItemHeight() * 10;
			data.horizontalSpan = 2;

			TableColumn colName = new TableColumn(table, SWT.LEFT);
			colName.setText("name");
			colName.setWidth(150);

			TableColumn colVersion = new TableColumn(table, SWT.LEFT);
			colVersion.setText("version");
			colVersion.setWidth(100);

			TableColumn colPath = new TableColumn(table, SWT.LEFT);
			colPath.setText("full-path");
			colPath.setWidth(400);

			table.setLayoutData(data);
			String classpath = System.getProperty("java.class.path");
			StringTokenizer tokenizer = new StringTokenizer(classpath, File.pathSeparator);
			while (tokenizer.hasMoreTokens()) {
				TableItem item = new TableItem(table, SWT.NONE);
				String path = tokenizer.nextToken();
				File file = new File(path);
				item.setText(0, file.isFile() ? file.getName() : "<dir>");
				item.setText(1, getVersion(file));
				item.setText(2, path);
			}

			return super.createDialogArea(parent);
		}

		String getVersion(File file) {
			if (file.isFile()) {
				ZipFile zipFile = null;
				try {
					zipFile = new ZipFile(file);
					ZipEntry entry = zipFile.getEntry("META-INF/MANIFEST.MF");
					if (entry != null) {
						Manifest manifest = new Manifest(zipFile.getInputStream(entry));
						Attributes attributes = manifest.getMainAttributes();
						String version = attributes.getValue(Attributes.Name.SPECIFICATION_VERSION);
						if (!StringUtils.isEmpty(version)) {
							return version;
						}
						version = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
						if (!StringUtils.isEmpty(version)) {
							return version;
						}
						/* for eclipse jars */
						version = attributes.getValue(BUNDLE_VERSION);
						if (!StringUtils.isEmpty(version)) {
							return version;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (zipFile != null) {
						try {
							zipFile.close();
						} catch (IOException e) {
							/* ignore */
						}
					}
				}
			}
			return "?";
		}
	}
}
