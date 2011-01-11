package com.cgoab.offline.client.web;

import java.util.Map;

import org.apache.http.entity.mime.content.FileBody;

import com.cgoab.offline.model.Photo;
import com.cgoab.offline.util.StringUtils;

public class PhotoBinder extends AbstractFormBinder {

	public PhotoBinder() {
		super("AddPhoto");

		/* used properties */
		registerUsedProperty("upload_filename");
		registerUsedProperty("caption");
		registerUsedProperty("page_id");

		/* unused properties */
		registerUnusedProperty("submitted");
		registerUnusedProperty("update_timestamp");
		registerUnusedProperty("upload_url");
		registerUnusedProperty("o");
		registerUnusedProperty("include_in_random");
		registerUnusedProperty("from");
		registerUnusedProperty("v");
		registerUnusedProperty("button");
		registerUnusedProperty("command");
		registerUnusedProperty("filename");
		registerUnusedProperty("doctype");
		registerUnusedProperty("border");
		registerUnusedProperty("rotate_degrees");
	}

	public Map<String, Object> bind(Photo photo, FileBody file, int pageId) {
		ParamaterBuilder fields = newCollector();
		fields.put("upload_filename", file);
		fields.put("caption", StringUtils.nullToEmpty(photo.getCaption()));
		fields.put("page_id", Integer.toString(pageId));
		return fields.getMap();
	}
}