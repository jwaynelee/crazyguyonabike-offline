package testutils;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TestLogSetup {
	private static boolean configured;

	public static final void configure() {
		if (!configured) {
			configured = true;
			BasicConfigurator.configure();
			/* quiet */
			Logger.getLogger("org.apache.http.wire").setLevel(Level.WARN);
		}
	}
}
