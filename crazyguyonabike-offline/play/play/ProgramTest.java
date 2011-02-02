package play;

import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class ProgramTest {
	public static void main(String[] args) {
		Display d = new Display();
		Shell s = new Shell(d);
		Program.launch("http://www.google.com");
		s.open();
		while (!s.isDisposed()) {
			if (!d.readAndDispatch())
				d.sleep();
		}
	}
}
