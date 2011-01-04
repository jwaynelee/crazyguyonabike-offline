package play;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class ProgressBarBug {
	private static ProgressBar bar;

	public static void main(String[] args) {
		final Display d = new Display();
		Shell s = new Shell(d);
		s.setLayout(new GridLayout());
		final Text text = new Text(s, SWT.READ_ONLY);
		text.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		bar = new ProgressBar(s, SWT.SMOOTH);
		bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

		s.open();

		d.timerExec(100, new Runnable() {
			int i = 0;

			@Override
			public void run() {
				bar.setSelection(i);
				text.setText("" + i);
				if (i < 100) {
					d.timerExec(50, this);
				}
				i += 20;
			}
		});

		// loop
		s.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent arg0) {
				d.dispose();
			}
		});
		while (!d.isDisposed()) {
			if (!d.readAndDispatch()) {
				d.sleep();
			}
		}
	}
}
