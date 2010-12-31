package com.cgoab.offline.client;

import com.cgoab.offline.model.Photo;

public interface PhotoUploadProgressListener {
	void uploadPhotoProgress(Photo photo, long bytes, long total);
}