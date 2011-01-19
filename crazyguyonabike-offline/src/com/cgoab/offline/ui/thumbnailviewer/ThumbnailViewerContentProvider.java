package com.cgoab.offline.ui.thumbnailviewer;

public interface ThumbnailViewerContentProvider {

	public void dispose();

	public Object[] getThumbnails(Object input);

	public void inputChanges(ThumbnailViewer viewer, Object oldInput, Object newInput);
}
