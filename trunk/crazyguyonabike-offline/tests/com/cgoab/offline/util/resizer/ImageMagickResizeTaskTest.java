package com.cgoab.offline.util.resizer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.apache.log4j.BasicConfigurator;
import org.junit.Assert;
import org.junit.Test;

import testdata.photos.TestPhotos;

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
		new ImageMagickResizeTask(cmd, source, target, null, null).call();
	}

	@Test
	public void testResizeSucess() throws Exception {

		// prepare for the test
		File source = TestPhotos.getPhotoAsTempFile();
		File target = File.createTempFile("out_", null);
		source.deleteOnExit();
		target.deleteOnExit();

		Metadata sourceMeta = JpegMetadataReader.readMetadata(source);
		JpegDirectory sjDir = (JpegDirectory) sourceMeta.getDirectory(JpegDirectory.class);
		Assert.assertEquals(2232, sjDir.getImageHeight());
		Assert.assertEquals(3968, sjDir.getImageWidth());
		execute(source, target);
		Assert.assertTrue(target.exists());

		// load up target and check size
		Metadata targetMeta = JpegMetadataReader.readMetadata(target);
		JpegDirectory tjDir = (JpegDirectory) targetMeta.getDirectory(JpegDirectory.class);
		Assert.assertEquals(1400, tjDir.getImageHeight());
		Assert.assertEquals(2489, tjDir.getImageWidth());
	}

	@Test
	public void testResizeBogusFileFails() throws IOException {
		File source = File.createTempFile("in_bogus", null);
		File target = File.createTempFile("out_bogus", null);
		// create bogus jpeg file
		TestPhotos.copyToTempFile(source, new ByteArrayInputStream(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0x00,
				(byte) 0x00 }));
		Exception failure = null;
		try {
			execute(source, target);
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