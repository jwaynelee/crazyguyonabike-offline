package com.cgoab.offline.ui.actions;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.cgoab.offline.util.NewFileAppender;

public class OpenLogFileAction extends Action {
	private Shell shell;

	public OpenLogFileAction(Shell shell) {
		super("Open logfile");
		this.shell = shell;
	}

	@Override
	public void run() {
		/* log file is stashed in system properties */
		String logFile = System.getProperty(NewFileAppender.FILE);
		// MessageDialog.openInformation(null, "Log file", logFile);
		LogViewer viewer = new LogViewer(shell);
		viewer.setLogFile(logFile);
		viewer.setBlockOnOpen(false);
		viewer.open();
		return;
	}

	private static final class LogViewer extends ApplicationWindow {

		private String file;

		private StyledText text;

		public LogViewer(Shell shell) {
			super(shell);
		}

		public void setLogFile(String logFile) {
			this.file = logFile;
		}

		@Override
		protected Control createContents(Composite parent) {
			Composite window = new Composite(parent, SWT.NONE);
			window.setLayout(new GridLayout());
			text = new StyledText(window, SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
			text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			new Label(window, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
					false));
			Composite buttons = new Composite(window, SWT.NONE);
			buttons.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			buttons.setLayout(new RowLayout());
			Button refreshButton = new Button(buttons, SWT.PUSH);
			refreshButton.setText("Refresh");
			refreshButton.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					refresh();
				}
			});
			refresh();
			return parent;
		}

		private void refresh() {
			text.setText("");
			FileReader r = null;
			try {
				r = new FileReader(file);
				BufferedReader reader = new BufferedReader(r);
				String line;
				while ((line = reader.readLine()) != null) {
					text.append(line);
					text.append(Text.DELIMITER);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				if (r != null) {
					try {
						r.close();
					} catch (IOException e) {
						/* ignore */
					}
				}
			}
		}
	}
}
