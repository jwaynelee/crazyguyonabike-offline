package testutils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.swt.widgets.Display;

public class DisplayThread {
	Display display;

	public DisplayThread() throws TimeoutException {
		final CountDownLatch latch = new CountDownLatch(1);
		new Thread(new Runnable() {
			@Override
			public void run() {
				display = new Display();
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						latch.countDown();
					}
				});
				while (!display.isDisposed()) {
					if (!display.readAndDispatch()) {
						display.sleep();
					}
				}
			}
		}, "DisplayThread").start();
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new TimeoutException("Waited 5 seconds for loop to start!");
			}
		} catch (InterruptedException e) {
			/* ignore */
		}
	}

	public void destroy() {
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				display.dispose();
			}
		});
	}

	public Display getDisplay() {
		return display;
	}

	public static void main(String[] args) throws Exception {
		final DisplayThread t = new DisplayThread();
		t.getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				System.out.println("HELLO WORLD!");
				t.destroy();
			}
		});
	}
}
