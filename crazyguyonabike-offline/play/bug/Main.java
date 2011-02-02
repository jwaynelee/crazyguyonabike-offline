package bug;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class Main {

	public static void main(String args[]) {
		Display d = new Display();
		Image i = new Image(d, "D:\\virtual\\shared\\photos\\large\\P1070012.JPG");
		System.out.println("width=" + i.getBounds().width + ", height=" + i.getBounds().height);
		d.dispose();
		System.exit(0);
	}
}
