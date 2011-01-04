package play;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;

public class RadioButtonBug {
	public static void main(String[] args) {
		Display d = new Display();
		Shell s = new Shell(d);
		s.setLayout(new RowLayout());
		Group g = new Group(s, SWT.NONE);
		g.setLayout(new FillLayout());
		Button a = new Button(g, SWT.RADIO);
		a.setText("a");
		Button b = new Button(g, SWT.RADIO);
		b.setText("b");
		final Button c = new Button(g, SWT.RADIO);
		c.setText("c");
		Button programatic = new Button(s, SWT.PUSH);
		programatic.setText("Select C");
		programatic.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				c.setSelection(true);
			}
		});
		s.pack();
		s.open();

		while (!s.isDisposed()) {
			if (!d.readAndDispatch()) {
				d.sleep();
			}
		}
		d.dispose();
	}
}
