package com.cgoab.offline.model;

import java.util.Comparator;

class FileNameComparator implements Comparator<Photo> {

	static final Comparator<Photo> INSTANCE = new FileNameComparator();

	@Override
	public int compare(Photo o1, Photo o2) {
		return o1.getFile().compareTo(o2.getFile());
	}
}