import java.awt.Desktop;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class Main {
	public static void main(String args[]) {
		Display d = new Display();
		Shell s = new Shell(d);
		if (args.length > 0) {
			System.out.println(Desktop.isDesktopSupported());
		}
		s.open();
		while (!s.isDisposed()) {
			if (!d.readAndDispatch()) {
				d.sleep();
			}
		}
		d.dispose();
	}
}