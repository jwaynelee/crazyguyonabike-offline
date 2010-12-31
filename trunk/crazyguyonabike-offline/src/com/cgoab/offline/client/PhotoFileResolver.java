package com.cgoab.offline.client;

import java.io.File;

import com.cgoab.offline.model.Photo;

/**
 * Resolves the file to upload to the server for a given photo, used to offer a
 * cached or smaller version of the photo to reduce upload time.
 */
public interface PhotoFileResolver {
	public File getFileFor(Photo photo);
}
