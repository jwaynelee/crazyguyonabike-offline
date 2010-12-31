package com.cgoab.offline.ui;

import java.io.File;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.model.Photo;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewerLabelProvider;

public class PhotosLabelProvider implements ThumbnailViewerLabelProvider {

	@Override
	public File getImageFile(Object thumbnail) {
		return ((Photo) thumbnail).getFile();
	}

	@Override
	public String getImageText(Object thumbnail) {
		return ((Photo) thumbnail).getFile().getName();
	}

	@Override
	public int getOpacity(Object o) {
		Photo photo = (Photo) o;
		switch (photo.getState()) {
		case UPLOADED:
			return 127;
		case ERROR:
			return 127 + 64;
		}
		return 255;
	}

	@Override
	public Image getOverlay(Object o) {
		Photo p = (Photo) o;

		switch (p.getState()) {
		case ERROR:
			return new Image(Display.getCurrent(), getClass().getResourceAsStream("/icons/error.gif"));
		case UPLOADED:
			return new Image(Display.getCurrent(), getClass().getResourceAsStream("/icons/locked.gif"));
		}

		return null;
	}
}
