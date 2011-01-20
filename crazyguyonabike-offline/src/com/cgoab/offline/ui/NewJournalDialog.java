package com.cgoab.offline.ui;

import java.io.File;
import java.io.IOException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class NewJournalDialog {

	public static final String EXTENSION = ".xml";

	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		NewJournalDialog d = new NewJournalDialog(shell);
		d.open();
		System.out.println(d.getName());
	}

	private String location;
	private String name;

	private int result = SWT.CANCEL;

	private Shell shell;

	public NewJournalDialog(Shell parent) {
		shell = new Shell(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM);
	}

	private void error(String msg) {
		MessageDialog.openError(shell, "Error creating new journal", msg);
	}

	// TODO handle null home?
	protected String getDefaultLocation(String name) {
		String home = System.getProperty("user.home");
		return home + File.separator + "crazyguyonabike" + File.separator + name + EXTENSION;
	}

	public String getLocation() {
		return location;
	}

	public String getName() {
		return name;
	}

	public int open() {
		shell.setText("Create new Journal");
		GridLayout layout = new GridLayout(2, false);
		layout.marginLeft = layout.marginRight = 6;
		layout.marginBottom = layout.marginTop = 6;

		shell.setLayout(layout);

		Label message = new Label(shell, SWT.NONE);
		message.setText("Enter a new Journal name");
		GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		message.setLayoutData(data);

		Label line = new Label(shell, SWT.SEPARATOR | SWT.SHADOW_OUT | SWT.HORIZONTAL);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		line.setLayoutData(data);

		Label nameLabel = new Label(shell, SWT.NONE);
		nameLabel.setText("Name: ");
		final Text nameText = new Text(shell, SWT.SINGLE | SWT.BORDER);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.minimumWidth = 300;
		nameText.setLayoutData(data);

		final Button defaultBox = new Button(shell, SWT.CHECK);
		defaultBox.setText("default");
		data = new GridData(SWT.LEFT, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		defaultBox.setLayoutData(data);
		defaultBox.setSelection(true);

		final Label locationLabel = new Label(shell, SWT.NONE);
		locationLabel.setText("Location: ");
		locationLabel.setEnabled(false);
		final Text locationText = new Text(shell, SWT.SINGLE | SWT.BORDER);
		locationText.setEnabled(false);
		locationText.setText(getDefaultLocation(""));

		defaultBox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean isChecked = defaultBox.getSelection();
				locationText.setEnabled(!isChecked);
				locationLabel.setEnabled(!isChecked);
				if (isChecked) {
					locationText.setText(getDefaultLocation(nameText.getText()));
				} else {
					locationText.setText("");
				}
			}
		});

		// auto-completion of location
		nameText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (defaultBox.getSelection()) {
					// update location
					locationText.setText(getDefaultLocation(nameText.getText()));
				}
			}
		});

		nameText.addVerifyListener(new VerifyListener() {

			@Override
			public void verifyText(VerifyEvent e) {
				// only allowed [0-9a-Z_-]
				String text = e.text;
				for (int i = 0; i < text.length(); ++i) {
					char c = text.charAt(i);
					if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
						continue;
					} else {
						e.doit = false;
						return;
					}
				}
				e.doit = true;
			}
		});

		locationText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Label line2 = new Label(shell, SWT.SEPARATOR | SWT.SHADOW_OUT | SWT.HORIZONTAL);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		line2.setLayoutData(data);

		Composite buttonComposite = new Composite(shell, SWT.NONE);
		buttonComposite.setLayout(new RowLayout());
		data = new GridData(SWT.CENTER, SWT.CENTER, true, false);
		data.horizontalSpan = 2;
		buttonComposite.setLayoutData(data);

		Button btnOK = new Button(buttonComposite, SWT.PUSH);
		btnOK.setText("OK");
		btnOK.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				name = nameText.getText();
				location = locationText.getText();
				if (verifyBeforeClose()) {
					result = SWT.OK;
					shell.close();
				}
			}
		});
		Button btnCancel = new Button(buttonComposite, SWT.PUSH);
		btnCancel.setText("Cancel");
		btnCancel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				result = SWT.CANCEL;
				shell.close();
			}
		});

		shell.setDefaultButton(btnOK);
		shell.pack();
		shell.open();

		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch()) {
				shell.getDisplay().sleep();
			}
		}

		return result;
	}

	protected boolean verifyBeforeClose() {
		if (name == null || name.isEmpty()) {
			error("Journal name must not be empty!");
			return false;
		}
		if (!location.endsWith(EXTENSION)) {
			error("Location must end in " + EXTENSION);
			return false;
		}
		File file = new File(location);
		if (!file.isAbsolute()) {
			error("File [" + location + "] is not an absolute path!");
			return false;
		}
		if (file.exists()) {
			error("File [" + location + "] already exists!");
			return false;
		}
		File parent = file.getParentFile();
		if (!parent.exists()) {
			// attempt to create parent directories
			if (!parent.mkdirs()) {
				error("Failed to create parent directory(s) [" + parent.getAbsolutePath() + "]");
			}
		}
		try {
			file.createNewFile();
		} catch (IOException e) {
			error("Failed to create new file at [" + location + "] : " + e.getMessage());
			return false;
		}

		return true;
	}
}
