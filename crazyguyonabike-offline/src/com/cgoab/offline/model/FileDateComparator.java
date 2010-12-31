package com.cgoab.offline.model;

import java.util.Comparator;

class FileDateComparator implements Comparator<Photo> {

	static final Comparator<Photo> INSTANCE = new FileDateComparator();

	@Override
	public int compare(Photo o1, Photo o2) {
		return (int) (o1.getFile().lastModified() - o2.getFile().lastModified());
	}
}
