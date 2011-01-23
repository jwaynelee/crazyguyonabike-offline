package com.cgoab.offline.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.ErrorCode;

/**
 * LOG4J extension, creates a new log file each time the application is run.
 * 
 * Log files have system timestamp appended to make them unique.
 */
public class NewFileAppender extends RollingFileAppender {

	public static final String FILE = NewFileAppender.class.getName() + "#FILE";

	private int logFilesToSave = 5;

	@Override
	public void activateOptions() {
		String defaulFile = fileName;
		if (defaulFile != null) {
			String instanceFileName = defaulFile + "_" + System.currentTimeMillis();
			try {
				setFile(instanceFileName, fileAppend, bufferedIO, bufferSize);
				System.setProperty(FILE, instanceFileName);
				deleteOldFiles(defaulFile);
			} catch (java.io.IOException e) {
				errorHandler.error("setFile(" + instanceFileName + "," + fileAppend + ") call failed.", e,
						ErrorCode.FILE_OPEN_FAILURE);
			}
		} else {
			// LogLog.error("File option not set for appender ["+name+"].");
			LogLog.warn("File option not set for appender [" + name + "].");
			LogLog.warn("Are you using FileAppender instead of ConsoleAppender?");
		}
	}

	// delete oldest if > N files
	private void deleteOldFiles(String filePrefix) {
		String prefix = new File(filePrefix).getName();
		File current = new File(getFile());
		List<File> oldFiles = new ArrayList<File>();
		for (File f : current.getParentFile().listFiles()) {
			if (f.equals(current)) {
				// ignore current
			} else if (f.getName().startsWith(prefix)) {
				oldFiles.add(f);
			}
		}

		if (oldFiles.size() > logFilesToSave) {
			// order, oldest first
			Collections.sort(oldFiles, new Comparator<File>() {
				@Override
				public int compare(File o1, File o2) {
					return (int) (o1.lastModified() - o2.lastModified());
				}
			});
			while (oldFiles.size() > logFilesToSave) {
				File oldFile = oldFiles.remove(0);
				if (!oldFile.delete()) {
					LogLog.warn("Could not delete old log file [" + oldFile.getName() + "]");
				} else {
					LogLog.warn("Deleted old log file [" + oldFile.getName() + "]");
				}
			}
		}
	}
}
