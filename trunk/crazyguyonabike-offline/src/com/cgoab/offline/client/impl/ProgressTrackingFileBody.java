package com.cgoab.offline.client.impl;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.entity.mime.content.FileBody;

public class ProgressTrackingFileBody extends FileBody {

	private final ProgressListener listener;

	public ProgressTrackingFileBody(File f, final ProgressListener listener) {
		super(f);
		this.listener = listener;
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		super.writeTo(new CountingOutputStream(out, this.listener, getContentLength()));
	}

	public static class CountingOutputStream extends FilterOutputStream {

		private final ProgressListener listener;

		private long transferred;

		private long total;

		public CountingOutputStream(final OutputStream out, final ProgressListener listener, long total) {
			super(out);
			this.listener = listener;
			this.transferred = 0;
			this.total = total;
		}

		public void write(byte[] b, int off, int len) throws IOException {
			out.write(b, off, len);
			this.transferred += len;
			this.listener.transferred(this.transferred, this.total);
		}

		public void write(int b) throws IOException {
			out.write(b);
			this.transferred++;
			this.listener.transferred(this.transferred, this.total);
		}
	}

	public static interface ProgressListener {
		void transferred(long sent, long total);
	}
}
