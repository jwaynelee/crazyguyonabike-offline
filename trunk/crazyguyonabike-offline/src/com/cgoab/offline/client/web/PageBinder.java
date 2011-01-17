package com.cgoab.offline.client.web;

import java.util.Map;

import com.cgoab.offline.model.Page;

public class PageBinder extends AbstractFormBinder {

	private static final String SUBMIT_VALUE = "Upload Pic or File";
	private int documentId;

	public PageBinder() {
		super("EditPage");

		/* what we expect the form to look like */
		registerUsedProperty("toc_heading_size");
		registerUsedProperty("toc_heading_bold");
		registerUsedProperty("toc_heading_italic");
		registerUsedProperty("toc_level");
		registerUsedProperty("visible");
		registerUsedProperty("date");
		registerUsedProperty("distance");
		registerUsedProperty("title");
		registerUsedProperty("headline");
		registerUsedProperty("text");
		registerUsedProperty("edit_comment");
		registerUsedProperty("format");
		registerUsedPropertyOption("submit", SUBMIT_VALUE);
		registerUsedProperty("doc_id");
		registerUsedProperty("update_timestamp");
		registerUsedProperty("sequence");

		/* unused fields */
		registerUsedProperty("submitted");
		registerUsedProperty("o");
		registerUsedProperty("from");
		registerUsedProperty("command");
		registerUsedProperty("doctype");
		registerUsedProperty("locales_format");
	}

	public Map<String, Object> bind(Page page) {
		ParamaterBuilder builder = newCollector();
		builder.put("toc_heading_size", page.getHeadingStyle().toString());
		builder.put("toc_heading_bold", toOnOff(page.isBold()));
		builder.put("toc_heading_italic", toOnOff(page.isItalic()));
		builder.put("toc_level", Integer.toString(page.getIndent()));
		builder.put("visible", toOnOff(page.isVisible()));
		builder.put("date", page.getDate().toString());
		builder.put("distance", Integer.toString(page.getDistance()));
		builder.put("title", page.getTitle());
		builder.put("headline", page.getHeadline());
		builder.put("text", page.getText());
		builder.put("edit_comment", page.getEditComment());
		builder.put("format", page.getFormat().toString().toLowerCase());
		builder.put("submit", SUBMIT_VALUE);
		builder.put("doc_id", Integer.toString(documentId));
		builder.put("update_timestamp", "off");
		/*
		 * remove explicit sequence as server appears to allow this and defaults
		 * to the last page location (which is what we want)
		 */
		builder.remove("sequence");
		return builder.getMap();
	}

	public void setDocumentId(int documentId) {
		this.documentId = documentId;
	}

	@Override
	public boolean isInitialized() {
		return super.isInitialized() && documentId != -1;
	}
}