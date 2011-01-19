package testutils;

import org.junit.Assert;
import org.junit.Test;

public class TestUtilsTest {
	@Test
	public void getTestName() {
		Assert.assertEquals(getClass().getName() + "_getTestName", TestUtils.getTestName());
	}

	@Test
	public void getTestName_nested() {
		doGetTestNameNested();
	}

	private void doGetTestNameNested() {
		/* also tests for private access */
		Assert.assertEquals(getClass().getName() + "_getTestName_nested", TestUtils.getTestName());
	}
}
