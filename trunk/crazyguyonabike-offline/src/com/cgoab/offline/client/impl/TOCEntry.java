package com.cgoab.offline.client.impl;

public class TOCEntry {
	private String title, headline;
	private int sequence, indent;
	private int id;

	public TOCEntry(String title, String headline, int sequence, int indent, int id) {
		this.title = title;
		this.headline = headline;
		this.sequence = sequence;
		this.indent = indent;
		this.id = id;
	}

	@Override
	public String toString() {
		StringBuffer line = new StringBuffer();
		for (int i = 1; i < indent; ++i) {
			line.append("  ");
		}
		line.append("* ").append(title);
		if (!"".equals(headline)) {
			line.append(" : ").append(headline);
		}
		line.append(" (seq").append(sequence).append(", ");
		line.append("ind=").append(indent).append(", ");
		line.append("id=").append(id).append(")");
		return line.toString();
	}
}
