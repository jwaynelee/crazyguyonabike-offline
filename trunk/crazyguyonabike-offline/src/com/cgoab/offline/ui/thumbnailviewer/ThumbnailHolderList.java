package com.cgoab.offline.ui.thumbnailviewer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ThumbnailHolderList implements Iterable<ThumbnailHolder> {

	private List<ThumbnailHolder> thumbnails = new ArrayList<ThumbnailHolder>();

	public int size() {
		return thumbnails.size();
	}

	public int indexOf(Object o) {
		for (int i = 0; i < thumbnails.size(); ++i) {
			if (thumbnails.get(i).getData() == o) {
				return i;
			}
		}
		return -1;
	}

	public int indexOf(ThumbnailHolder holder) {
		return thumbnails.indexOf(holder);
	}

	public void append(ThumbnailHolder h) {

	}

	public void insert(ThumbnailHolder h, int insertionPoint) {

	}

	void updateFrom(int start) {
		int x = start < 1 ? ThumbnailViewer.PADDING_BETWEEN_THUMNAIL : thumbnails.get(start - 1).getX();
		for (int i = start; i < thumbnails.size(); ++i) {
			ThumbnailHolder h = thumbnails.get(i);
			h.setX(x + h.getWidth() + ThumbnailViewer.PADDING_BETWEEN_THUMNAIL);
		}
	}

	@Override
	public Iterator<ThumbnailHolder> iterator() {
		return thumbnails.iterator();
	}

	@Override
	public String toString() {
		return thumbnails.toString();
	}
}
