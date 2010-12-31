package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;

import org.eclipse.swt.graphics.Image;

public interface ThumbnailViewerLabelProvider {
	public File getImageFile(Object thumbnail);

	public String getImageText(Object thumbnail);

	public Image getOverlay(Object o);

	public int getOpacity(Object o);
}
