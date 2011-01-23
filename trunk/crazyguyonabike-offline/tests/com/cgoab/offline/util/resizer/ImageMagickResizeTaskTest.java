package com.cgoab.offline.util.resizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.apache.log4j.BasicConfigurator;
import org.junit.Assert;
import org.junit.Test;

import testutils.TestUtils;
import testutils.photos.TestPhotos;

import com.cgoab.offline.util.Which;
import com.cgoab.offline.util.resizer.ImageMagickResizeTask.MagickException;
import com.cgoab.offline.util.resizer.ImageMagickResizeTask.MagickVersion;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.jpeg.JpegDirectory;

public class ImageMagickResizeTaskTest {

	static {
		BasicConfigurator.configure();
	}

	@Test
	public void testMagickVersion() {
		MagickVersion v6667 = new MagickVersion(6, 6, 6, 7);
		MagickVersion v6388 = new MagickVersion(6, 3, 8, 8);

		// like with like
		Assert.assertTrue(v6667.isAtLeast(v6667));

		Assert.assertTrue(v6667.isAtLeast(v6388));
		Assert.assertFalse(v6388.isAtLeast(v6667));
	}

	private void execute(File source, File target) throws Exception {
		// manually resolve the executable
		String cmd = Which.find(ImageMagickResizeTask.MAGICK_COMMAND);
		Assert.assertNotNull(cmd);
		new ImageMagickResizeTask(new File(cmd).getParent(), ImageMagickResizerServiceFactory.DEFAULT_SIZE,
				ImageMagickResizerServiceFactory.DEFAULT_JPEG_QUALITY, source, target, null, null).call();
	}

	@Test
	public void testResizeSucess() throws Exception {

		// prepare for the test
		File source = TestPhotos.extractLargePhoto();
		String targetStr = TestUtils.createTempFileName(source.getName());
		source.deleteOnExit();
		File target = new File(targetStr);

		Metadata sourceMeta = JpegMetadataReader.readMetadata(source);
		JpegDirectory sjDir = (JpegDirectory) sourceMeta.getDirectory(JpegDirectory.class);
		assertEquals(2232, sjDir.getImageHeight());
		assertEquals(3968, sjDir.getImageWidth());
		execute(source, target);
		assertTrue(target.exists());
		target.deleteOnExit();
		// load up target and check size
		Metadata targetMeta = JpegMetadataReader.readMetadata(target);
		JpegDirectory tjDir = (JpegDirectory) targetMeta.getDirectory(JpegDirectory.class);
		assertEquals(1000, tjDir.getImageHeight());
		assertEquals(1778, tjDir.getImageWidth());

		// check aspect ratio was maintained
		float sourceAspect = (float) sjDir.getImageWidth() / sjDir.getImageHeight();
		float targetAspect = (float) tjDir.getImageWidth() / tjDir.getImageHeight();
		assertEquals(sourceAspect, targetAspect, 0.001); /*
														 * 1 pixel in 1000
														 */
	}

	@Test
	public void testResizeBogusFileFails() throws IOException {
		File source = File.createTempFile("in_bogus", null);
		String target = TestUtils.createTempFileName(source.getName());

		// create bogus jpeg file
		TestPhotos.copyToTempFile(source, new ByteArrayInputStream(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0x00,
				(byte) 0x00 }));
		Exception failure = null;
		try {
			execute(source, new File(target));
		} catch (MagickException e) {
			/* expected */
			failure = e;
		} catch (Exception e) {
			/* not expected */
			throw new RuntimeException(e);
		}
		Assert.assertNotNull(failure);
	}

}
