package com.cgoab.offline.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import nu.xom.ValidityException;

import org.joda.time.LocalDate;

import com.cgoab.offline.model.Page.EditFormat;
import com.cgoab.offline.model.Page.HeadingStyle;
import com.cgoab.offline.model.Page.PhotosOrder;
import com.cgoab.offline.util.Assert;
import com.cgoab.offline.util.StringUtils;

/**
 * Loads (and saves) journals in an XML format.
 */
public class JournalXmlLoader {

	/* file format version */
	public enum FileVersion {

		V1("1.0");

		private String versionString;

		public static FileVersion parse(String s) {
			for (FileVersion v : FileVersion.values()) {
				if (v.versionString.equals(s)) {
					return v;
				}
			}
			throw new IllegalArgumentException("Unsupported data version [" + s + "]");
		}

		private FileVersion(String verstionString) {
			this.versionString = verstionString;
		}

		@Override
		public String toString() {
			return versionString;
		}
	};

	private static final FileVersion CURRENT_VERSION = FileVersion.V1;

	private static final String SETTINGS_EL = "settings";
	private static final String VERSION_ATTR = "version";
	private static final String JOURNAL_EL = "journal";
	private static final String DOC_ID_HINT_ATTR = "docIdHint";
	private static final String RESIZE_PHOTOS_ATTR = "resizeBeforeUpload";
	private static final String HIDE_UPLOADED_CONTENT_ATTR = "hideUploadedContent";
	private static final String USE_EXIF_THUMBNAIL_ATTR = "useExifThumbnail";
	private static final String NAME_ATTR = "name";
	private static final String PAGE_EL = "page";
	private static final String UPLOAD_STATE_ATTR = "state";
	private static final String LOCAL_ID_ATTR = "localId";
	private static final String SERVER_ID_ATTR = "serverId";
	private static final String VISIBLE_ATTR = "visible";
	private static final String FORMAT_ATTR = "format";
	private static final String HEADINGSTYLE_ATTR = "headingstyle";
	private static final String ITALIC_ATTR = "italic";
	private static final String BOLD_ATTR = "bold";
	private static final String INDENT_ATTR = "indent";
	private static final String TITLE_EL = "title";
	private static final String HEADLINE_EL = "headline";
	private static final String DISTANCE_EL = "distance";
	private static final String DATE_EL = "date";
	private static final String TEXT_EL = "text";
	private static final String PHOTOS_EL = "photos";
	private static final String PHOTO_EL = "photo";
	private static final String FILE_ATTR = "file";
	// private static final String IMAGENAME_ATTR = "imagename";
	private static final String CAPTION_EL = "caption";
	private static final String PHOTOS_ORDER_ATTR = "photosOrder";

	private static int getAttributeOrDefault(Element xml, String name, int defaultValue) {
		Attribute attr = xml.getAttribute(name);
		return attr == null ? defaultValue : Integer.parseInt(attr.getValue());
	}

	public static Journal open(File file) throws ValidityException, ParsingException, IOException {
		Document xml = new Builder(false).build(file);
		String version = xml.getRootElement().getAttributeValue(VERSION_ATTR);
		Assert.isTrue(FileVersion.parse(version) == CURRENT_VERSION);
		String name = xml.getRootElement().getAttributeValue(NAME_ATTR);
		Journal journal = new Journal(file, name);
		journal.setLastModifiedWhenLoaded(file.lastModified());

		/* parse settings (TODO move to preferences?) */
		Elements settings = xml.getRootElement().getChildElements(SETTINGS_EL);
		if (settings.size() > 0) {
			Element settingsXml = settings.get(0);
			// resize
			String resizeStr = settingsXml.getAttributeValue(RESIZE_PHOTOS_ATTR);
			if (resizeStr != null) {
				journal.setResizeImagesBeforeUpload(Boolean.parseBoolean(resizeStr));
			}
			// use-exif
			String useExifStr = settingsXml.getAttributeValue(USE_EXIF_THUMBNAIL_ATTR);
			if (useExifStr != null) {
				journal.setUseExifThumbnail(Boolean.parseBoolean(useExifStr));
			}
			// hide-uploaded
			String hideUploaded = settingsXml.getAttributeValue(HIDE_UPLOADED_CONTENT_ATTR);
			if (useExifStr != null) {
				journal.setHideUploadedContent(Boolean.parseBoolean(hideUploaded));
			}
		}

		/* parse pages & photos */
		journal.setDocIdHint(getAttributeOrDefault(xml.getRootElement(), DOC_ID_HINT_ATTR, Journal.UNSET_DOC_ID));
		Elements pages = xml.getRootElement().getChildElements(PAGE_EL);
		for (int i = 0; i < pages.size(); ++i) {
			Element elPage = pages.get(i);
			Page newPage = new Page(journal);
			newPage.setLocalId(Integer.parseInt(elPage.getAttributeValue(LOCAL_ID_ATTR)));
			newPage.setServerId(Integer.parseInt(elPage.getAttributeValue(SERVER_ID_ATTR)));
			newPage.setState(UploadState.valueOf(elPage.getAttributeValue(UPLOAD_STATE_ATTR)));
			newPage.setBold(Boolean.valueOf(elPage.getAttributeValue(BOLD_ATTR)));
			newPage.setItalic(Boolean.valueOf(elPage.getAttributeValue(ITALIC_ATTR)));
			newPage.setVisible(Boolean.valueOf(elPage.getAttributeValue(VISIBLE_ATTR)));
			newPage.setHeadingStyle(HeadingStyle.valueOf(elPage.getAttributeValue(HEADINGSTYLE_ATTR)));
			newPage.setIndent(Integer.valueOf(elPage.getAttributeValue(INDENT_ATTR)));
			newPage.setFormat(EditFormat.valueOf(elPage.getAttributeValue(FORMAT_ATTR)));

			// simple elements
			newPage.setTitle(StringUtils.trimToNull(elPage.getFirstChildElement(TITLE_EL).getValue()));
			newPage.setHeadline(StringUtils.trimToNull(elPage.getFirstChildElement(HEADLINE_EL).getValue()));
			newPage.setDistance(Float.parseFloat(elPage.getFirstChildElement(DISTANCE_EL).getValue()));
			newPage.setDate(new LocalDate(elPage.getFirstChildElement(DATE_EL).getValue()));

			Element textXml = elPage.getFirstChildElement(TEXT_EL);
			if (textXml != null) {
				newPage.setText(textXml.getValue());
			}

			// photos
			Element photosElem = elPage.getFirstChildElement(PHOTOS_EL);
			newPage.setPhotosOrder(PhotosOrder.valueOf(photosElem.getAttributeValue(PHOTOS_ORDER_ATTR)));
			Elements photosXml = photosElem.getChildElements(PHOTO_EL);
			List<Photo> photos = new ArrayList<Photo>(photosXml.size());
			for (int j = 0; j < photosXml.size(); ++j) {
				Element elPhoto = photosXml.get(j);
				Photo newPhoto = new Photo();
				newPhoto.setFile(new File(elPhoto.getAttributeValue(FILE_ATTR)));
				newPhoto.setState(UploadState.valueOf(elPhoto.getAttributeValue(UPLOAD_STATE_ATTR)));
				// newPhoto.setImageName(StringUtils.trimToNull(elPhoto.getAttributeValue(IMAGENAME_ATTR)));
				Element captionXml = elPhoto.getFirstChildElement(CAPTION_EL);
				if (captionXml != null) {
					newPhoto.setCaption(captionXml.getValue());
				}
				photos.add(newPhoto);
			}
			newPage.setPhotos(photos);
			journal.addPage(newPage);
		}

		// journal may become dirty as we add pages to it, mark "clean"
		journal.setDirty(false);
		return journal;
	}

	/**
	 * Enforces model constraints, used to check a model is valid before
	 * displaying it in the UI.
	 * 
	 * @param journal
	 * @throws AssertionError
	 */
	public static void validateJournal(Journal journal) {
		Assert.notNull(journal, "Journal is null!");
		Assert.notNull(journal.getFile(), "Unset journal file");
		Assert.notEmpty(journal.getName(), "Unset journal name");

		Page firstPageInError = null;
		Page firstNewPage = null;
		Page previousPage = null;
		List<Page> pages = journal.getPages();
		Set<Integer> localIds = new HashSet<Integer>();
		Set<Integer> serverIds = new HashSet<Integer>();

		for (int i = 0; i < pages.size(); ++i) {
			Page page = pages.get(i);
			Assert.same(journal, page.getJournal(), "Page is not related to the journal");
			int localId = page.getLocalId();

			// local-id
			Assert.isTrue(localId != Page.UNSET_LOCAL_ID, "unset local ID");
			Assert.isFalse(localIds.contains(page.getLocalId()), "duplicate local ID");
			localIds.add(localId);

			// server-id
			int serverId = page.getServerId();
			UploadState pageState = page.getState();
			if (serverId == Page.UNSET_SERVER_ID) {
				Assert.isTrue(pageState == UploadState.NEW || pageState == UploadState.ERROR,
						"page has no server ID but is not NEW|ERROR");
			} else {
				Assert.isTrue(pageState == UploadState.PARTIALLY_UPLOAD || pageState == UploadState.UPLOADED,
						"page has server ID but is not UPLOADED|PARTIALLY_UPLOADED");
				Assert.isFalse(serverIds.contains(serverIds), "duplicate server ID");
				serverIds.add(serverId);
			}

			// date
			if (previousPage != null) {
				Assert.isTrue(page.getDate().isAfter(previousPage.getDate()), "Page is older than previous page");
			}

			// page state
			if (pageState == UploadState.NEW) {
				if (firstNewPage == null) {
					firstNewPage = page;
				}
				// new pages can exist *after* error pages but not before
				for (Photo photo : page.getPhotos()) {
					Assert.isTrue(photo.getState() == UploadState.NEW, "Page is NEW but photo is '" + photo.getState()
							+ "'");
					verifyPhoto(photo);
				}
			} else if (pageState == UploadState.PARTIALLY_UPLOAD) {
				Assert.isNull(firstNewPage, "PARTIALLY_UPLOADED page found after NEW page");
				Assert.isNull(firstPageInError, "Multiple ERROR pages");
				firstPageInError = page;

				// one and only one photo should be in ERROR; only NEW
				// after, UPLOADED before
				Photo firstErrorPhoto = null;
				Photo firstNewPhoto = null;
				List<Photo> photos = page.getPhotos();
				for (int j = 0; j < photos.size(); ++j) {
					Photo photo = photos.get(j);
					if (photo.getState() == UploadState.NEW) {
						if (firstNewPage == null) {
							firstNewPhoto = photo;
						}
					} else if (photo.getState() == UploadState.UPLOADED) {
						Assert.isNull(firstErrorPhoto, "UPLOADED photo found after ERROR photo");
						Assert.isNull(firstNewPhoto, "UPLOADED photo found after NEW photo");
					} else if (photo.getState() == UploadState.ERROR) {
						Assert.isNull(firstErrorPhoto, "Multiple ERROR photos");
						firstErrorPhoto = photo;
						// new photos may appear before or after the error photo
					} else {
						Assert.isTrue(false, "Photo neither NEW|UPLOADED|ERROR");
					}
					verifyPhoto(photo);
				}

				// don't check for existence of error photo as it may have been
				// removed since upload
			} else if (pageState == UploadState.ERROR) {
				Assert.isNull(firstNewPage, "PARTIALLY_UPLOADED found after error page");
				Assert.isNull(firstPageInError, "Multiple ERROR pages");
				firstPageInError = page;
				for (Photo photo : page.getPhotos()) {
					Assert.isTrue(photo.getState() == UploadState.NEW, "Page is ERROR but photo is not NEW");
					verifyPhoto(photo);
				}
			} else if (pageState == UploadState.UPLOADED) {
				Assert.isNull(firstNewPage, "UPLOADED page found after NEW page");
				Assert.isNull(firstPageInError, "UPLOADED page found after ERROR|PARTIALLY_UPLOADED page");
				for (Photo photo : page.getPhotos()) {
					Assert.isTrue(photo.getState() == UploadState.UPLOADED,
							"Page is UPLOADED but photo is not UPLOADED");
					verifyPhoto(photo);
				}
			} else {
				Assert.isTrue(false, "Page is neither NEW|ERROR|PARTIALLY_UPLOADED|UPLOADED");
			}
			previousPage = page;
		}
	}

	private static void verifyPhoto(Photo p) {
		Assert.notNull(p, "Photo is null!");
		Assert.notNull(p.getFile(), "Photo has no file!");
	}

	private static void writeElement(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
		writer.writeStartElement(name);
		writer.writeCharacters(value);
		writer.writeEndElement();
	}

	private static void writeElementWithCData(XMLStreamWriter writer, String name, String value)
			throws XMLStreamException {
		writer.writeStartElement(name);
		writer.writeCData(value);
		writer.writeEndElement();
	}

	/**
	 * Writes the journal to file.
	 * 
	 * @param journal
	 * @throws IOException
	 * @throws XMLStreamException
	 */
	public static void save(Journal journal) throws IOException, XMLStreamException {
		XMLOutputFactory xof = XMLOutputFactory.newInstance();
		File tempFile = null;
		try {
			File targetFile = journal.getFile();
			File parent = targetFile.getAbsoluteFile().getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			tempFile = new File(parent + File.separator + "." + targetFile.getName());
			FileWriter tempWriter = new FileWriter(tempFile);
			XMLStreamWriter xml = xof.createXMLStreamWriter(tempWriter);
			xml.writeStartDocument();
			xml.writeStartElement(JOURNAL_EL);
			xml.writeAttribute(VERSION_ATTR, CURRENT_VERSION.toString());
			xml.writeAttribute(NAME_ATTR, journal.getName());
			xml.writeAttribute(DOC_ID_HINT_ATTR, Integer.toString(journal.getDocIdHint()));

			/* settings */
			xml.writeStartElement(SETTINGS_EL);
			if (journal.isResizeImagesBeforeUpload() != null) {
				xml.writeAttribute(RESIZE_PHOTOS_ATTR, journal.isResizeImagesBeforeUpload().toString());
			}
			if (journal.isUseExifThumbnail() != null) {
				xml.writeAttribute(USE_EXIF_THUMBNAIL_ATTR, journal.isUseExifThumbnail().toString());
			}
			xml.writeAttribute(HIDE_UPLOADED_CONTENT_ATTR, Boolean.toString(journal.isHideUploadedContent()));
			xml.writeEndElement();

			/* pages & photos */
			for (Page page : journal.getPages()) {
				xml.writeStartElement(PAGE_EL);
				xml.writeAttribute(LOCAL_ID_ATTR, Integer.toString(page.getLocalId()));
				xml.writeAttribute(SERVER_ID_ATTR, Integer.toString(page.getServerId()));
				xml.writeAttribute(UPLOAD_STATE_ATTR, page.getState().toString());
				xml.writeAttribute(VISIBLE_ATTR, Boolean.toString(page.isVisible()));
				xml.writeAttribute(FORMAT_ATTR, page.getFormat().toString());
				xml.writeAttribute(HEADINGSTYLE_ATTR, page.getHeadingStyle().toString());
				xml.writeAttribute(ITALIC_ATTR, Boolean.toString(page.isItalic()));
				xml.writeAttribute(BOLD_ATTR, Boolean.toString(page.isBold()));
				xml.writeAttribute(INDENT_ATTR, Integer.toString(page.getIndent()));
				writeElement(xml, TITLE_EL, StringUtils.nullToEmpty(page.getTitle()));
				writeElement(xml, HEADLINE_EL, StringUtils.nullToEmpty(page.getHeadline()));
				writeElement(xml, DISTANCE_EL, Float.toString(page.getDistance()));
				writeElement(xml, DATE_EL, page.getDate().toString());
				String text = page.getText();
				if (text != null) {
					writeElementWithCData(xml, TEXT_EL, text);
				}
				xml.writeStartElement(PHOTOS_EL);
				xml.writeAttribute(PHOTOS_ORDER_ATTR, page.getPhotosOrder().name());
				for (Photo p : page.getPhotos()) {
					xml.writeStartElement(PHOTO_EL);
					xml.writeAttribute(FILE_ATTR, p.getFile().getAbsolutePath());
					xml.writeAttribute(UPLOAD_STATE_ATTR, p.getState().toString());
					// xml.writeAttribute(IMAGENAME_ATTR,
					// StringUtils.nullToEmpty(p.getImageName()));
					String caption = p.getCaption();
					if (caption != null) {
						writeElementWithCData(xml, CAPTION_EL, caption);
					}
					xml.writeEndElement(); // </photo>
				}
				xml.writeEndElement(); // </photos>
				xml.writeEndElement(); // </page>
			}
			xml.writeEndElement(); // </journal>
			xml.writeEndDocument();
			xml.close();
			tempWriter.close();
			if (!tempFile.renameTo(targetFile.getAbsoluteFile())) {
				// already exists, copy manually
				copy(tempFile, targetFile);
			}
			journal.setLastModifiedWhenLoaded(targetFile.lastModified());
		} finally {
			if (tempFile != null) {
				tempFile.delete();
			}
		}
	}

	private static void copy(File tempFile, File targetFile) throws IOException {
		FileInputStream in = new FileInputStream(tempFile);
		FileOutputStream out = new FileOutputStream(targetFile);
		byte[] buff = new byte[8 * 1024]; // 8kb chunks
		int read;
		while ((read = in.read(buff)) > 0) {
			out.write(buff, 0, read);
		}
		in.close();
		out.close();
	}
}