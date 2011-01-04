package play;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class ProgressDialogTest {
	public static void main(String[] args) {
		Display d = new Display();
		final Shell s = new Shell(d);
		d.asyncExec(new Runnable() {
			@Override
			public void run() {
				ProgressMonitorDialog dialog = new ProgressMonitorDialog(s);
				try {
					dialog.run(true, true, new IRunnableWithProgress() {
						@Override
						public void run(IProgressMonitor monitor) throws InvocationTargetException,
								InterruptedException {
							monitor.beginTask("waiting", 100);
							while (!monitor.isCanceled()) {
								Thread.sleep(100);
								monitor.worked(10);
							}
						}
					});
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		while (!s.isDisposed()) {
			if (!d.readAndDispatch()) {
				d.sleep();
			}
		}
	}
}
