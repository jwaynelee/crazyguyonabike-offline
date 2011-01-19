package com.cgoab.offline.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class PreferencesDialog {

	private static void addSeparator(Shell shell) {
		new Label(shell, SWT.HORIZONTAL | SWT.SEPARATOR).setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
	}

	private Preferences preferences;

	private Shell shell;

	public PreferencesDialog(Shell parent) {
		shell = new Shell(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM);
	}

	public void open() {
		shell.setLayout(new GridLayout());
		Label text = new Label(shell, SWT.NONE);
		text.setText("Blah..");
		GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
		data.heightHint = 40;
		text.setLayoutData(data);
		addSeparator(shell);

		Group internalGroup = new Group(shell, SWT.NONE);
		internalGroup.setText("developer");
		internalGroup.setLayout(new RowLayout());
		Label mockLabel = new Label(internalGroup, SWT.NONE);
		mockLabel.setText("Use mock client?: ");
		final Button mockButton = new Button(internalGroup, SWT.CHECK);
		if (Boolean.valueOf(preferences.getValue(Preferences.USE_MOCK_IMPL_PATH))) {
			mockButton.setSelection(true);
		}

		addSeparator(shell);

		Composite buttons = new Composite(shell, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
		FillLayout layout = new FillLayout();
		layout.spacing = 5;
		buttons.setLayout(layout);
		Button btnOk = new Button(buttons, SWT.PUSH);
		btnOk.setText("OK");
		btnOk.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// update preferences
				preferences.setValue(Preferences.USE_MOCK_IMPL_PATH, Boolean.toString(mockButton.getSelection()));

				preferences.save();
				shell.close();
			}
		});

		Button btnCancel = new Button(buttons, SWT.PUSH);
		btnCancel.setText("Cancel");
		btnCancel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});

		// loop
		shell.open();
		shell.setMinimumSize(200, 200);
		shell.pack();
		while (!shell.isDisposed()) {
			Display display = shell.getDisplay();
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	public void setPreferences(Preferences preferences) {
		this.preferences = preferences;
	}

	// public static void main(String[] args) {
	// Display d = new Display();
	// Shell p = new Shell(d);
	// PreferencesDialog pref = new PreferencesDialog(p);
	// pref.setPreferences();
	// pref.open();
	// d.dispose();
	// }
}
