package com.cgoab.offline.ui.util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class ErrorBoxWithHtml {

	private String title;
	private String message;
	private Shell parent;
	private String html;

	public ErrorBoxWithHtml(Shell parent) {
		this.parent = parent;
	}

	public void setHtml(String html) {
		this.html = html;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void setParent(Shell parent) {
		this.parent = parent;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void open() {
		final Shell shell = new Shell(parent, SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.CLOSE);
		if (title != null) {
			shell.setText(title);
		}
		GridLayout layout = new GridLayout(2, false);
		layout.marginTop = layout.marginBottom = 5;
		layout.marginLeft = layout.marginRight = 5;
		shell.setLayout(layout);
		Label img = new Label(shell, SWT.NONE);
		img.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		img.setImage(shell.getDisplay().getSystemImage(SWT.ICON_ERROR));
		Label msg = new Label(shell, SWT.NONE);
		if (message != null) {
			msg.setText(message);
		}
		GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
		data.horizontalIndent = 20;
		msg.setLayoutData(data);
		Button btnOK = new Button(shell, SWT.PUSH);
		btnOK.setText("OK");
		shell.setDefaultButton(btnOK);
		data = new GridData(SWT.CENTER, SWT.CENTER, false, false, 2, 1);
		data.widthHint = 80;
		btnOK.setLayoutData(data);
		btnOK.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});
		Label separator = new Label(shell, SWT.HORIZONTAL | SWT.SEPARATOR);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		data.verticalIndent = 10;
		separator.setLayoutData(data);
		Label browserMessage = new Label(shell, SWT.NONE);
		browserMessage
				.setText("The page shown below was returned from the server, it may provide more detail on the error.");
		browserMessage.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 2, 1));
		Browser browser = new Browser(shell, SWT.BORDER);
		data = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		data.verticalIndent = 5;
		browser.setLayoutData(data);
		browser.setText(html, false);
		browser.setJavascriptEnabled(false);
		browser.addLocationListener(new LocationListener() {
			@Override
			public void changing(LocationEvent event) {
				event.doit = false;
			}

			@Override
			public void changed(LocationEvent event) {
			}
		});

		shell.open();

		Display d = shell.getDisplay();
		while (!shell.isDisposed()) {
			if (!d.readAndDispatch()) {
				d.sleep();
			}
		}
	}

	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		ErrorBoxWithHtml p = new ErrorBoxWithHtml(shell);
		p.setTitle("Error");
		p.setHtml("<h1>foo</h1>");
		p.setMessage("There was an error\na\nb\nc\nd\ne\nf");
		p.open();

		display.dispose();
	}
}
