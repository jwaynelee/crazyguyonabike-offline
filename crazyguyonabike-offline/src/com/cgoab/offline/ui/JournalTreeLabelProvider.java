package com.cgoab.offline.ui;

import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.util.StringUtils;

public class JournalTreeLabelProvider extends StyledCellLabelProvider {

	private Map<ImageDescriptor, Image> imageCache = new HashMap<ImageDescriptor, Image>();
	private Font boldFont;
	private Font italicFont;
	private Font boldItalicFont;
	private Shell shell;

	public JournalTreeLabelProvider(TreeViewer viewer, Shell shell) {
		this.shell = shell;
		FontData[] defaultFont = viewer.getTree().getFont().getFontData();
		boldFont = new Font(shell.getDisplay(), getModifiedFontData(defaultFont, SWT.BOLD));
		italicFont = new Font(shell.getDisplay(), getModifiedFontData(defaultFont, SWT.ITALIC));
		boldItalicFont = new Font(shell.getDisplay(), getModifiedFontData(defaultFont, SWT.BOLD | SWT.ITALIC));
	}

	private static FontData[] getModifiedFontData(FontData[] originalData, int additionalStyle) {
		FontData[] styleData = new FontData[originalData.length];
		for (int i = 0; i < styleData.length; i++) {
			FontData base = originalData[i];
			styleData[i] = new FontData(base.getName(), base.getHeight(), base.getStyle() | additionalStyle);
		}
		return styleData;
	}

	@Override
	public void update(ViewerCell cell) {

		Object obj = cell.getElement();
		if (obj instanceof Journal) {
			Journal journal = (Journal) obj;
			StringBuffer text = new StringBuffer();
			text.append(journal.getName());
			if (journal.isDirty()) {
				text.append(" *");
			}
			cell.setText(text.toString());
		} else if (obj instanceof Page) {
			Page page = (Page) obj;
			StringBuffer text = new StringBuffer();
			for (int i = 1; i < page.getIndent(); ++i) {
				text.append("-");
			}
			if (text.length() > 0) {
				text.append(" ");
			}
			text.append(page.getTitle());
			if (!StringUtils.isEmpty(page.getHeadline())) {
				text.append(" : ").append(page.getHeadline());
			}
			cell.setText(text.toString());
			StyleRange style = new StyleRange();
			style.start = 0;
			style.length = text.length();
			if (page.isBold() && page.isItalic()) {
				style.font = boldItalicFont;
			} else if (page.isBold()) {
				style.font = boldFont;
			} else if (page.isItalic()) {
				style.font = italicFont;
			}
			if (page.getState() == UploadState.UPLOADED) {
				style.foreground = shell.getDisplay().getSystemColor(SWT.COLOR_GRAY);
			}
			cell.setStyleRanges(new StyleRange[] { style });
		}
		cell.setImage(getImage(obj));
		super.update(cell);

	}

	private Image getImage(Object obj) {
		ImageDescriptor desc = null;
		if (obj instanceof Journal) {
			desc = getImageDescriptor("folder3.gif");
		}
		if (obj instanceof Page) {
			Page p = (Page) obj;
			switch (p.getState()) {
			case NEW:
				desc = getImageDescriptor("page.gif");
				break;
			case ERROR:
				desc = getImageDescriptor("error.gif");
				break;
			case PARTIALLY_UPLOAD:
				desc = getImageDescriptor("partialupload.gif");
				break;
			case UPLOADED:
				desc = getImageDescriptor("locked.gif");
				break;
			}
		}

		if (desc == null) {
			return null;
		}

		Image image = imageCache.get(desc);
		if (image == null) {
			image = desc.createImage();
			imageCache.put(desc, image);
		}
		return image;
	}

	public static ImageDescriptor getImageDescriptor(String name) {
		String iconPath = "/icons/";
		URL resource = PageEditor.class.getResource(iconPath + name);
		if (resource == null) {
			return ImageDescriptor.getMissingImageDescriptor();
		}
		return ImageDescriptor.createFromURL(resource);
	}

	public void dispose() {
		for (Iterator<Image> i = imageCache.values().iterator(); i.hasNext();) {
			((Image) i.next()).dispose();
		}
		imageCache.clear();
		super.dispose();
	}
}