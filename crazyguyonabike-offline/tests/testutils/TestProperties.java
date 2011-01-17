package testutils;

public class TestProperties {

	static {
		/* TODO default to true in eclipse false otherwise (ant) */
		waitForever = Boolean.getBoolean("test.waitForever");
	}

	public static final boolean waitForever;
}
