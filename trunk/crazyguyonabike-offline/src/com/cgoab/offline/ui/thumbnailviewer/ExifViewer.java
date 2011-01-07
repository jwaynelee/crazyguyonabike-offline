package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;

public class ExifViewer {
	private Shell shell;

	public ExifViewer(Shell parent) {
		shell = new Shell(parent);
	}

	public void open(final Metadata meta, File file) {
		shell.setText(file.getAbsolutePath());
		shell.setLayout(new FillLayout());
		TreeViewer viewer = new TreeViewer(shell, SWT.FULL_SELECTION);
		TreeViewerColumn cell1 = new TreeViewerColumn(viewer, SWT.NONE);
		cell1.getColumn().setText("Name");
		cell1.getColumn().setWidth(200);
		TreeViewerColumn cell2 = new TreeViewerColumn(viewer, SWT.NONE);
		cell2.getColumn().setText("Value");
		cell2.getColumn().setWidth(400);
		viewer.getTree().setHeaderVisible(true);
		viewer.getTree().setLinesVisible(true);
		// viewer.setColumnProperties(new String[] { "name", "value" });
		viewer.setContentProvider(new ITreeContentProvider() {
			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}

			@Override
			public void dispose() {
			}

			@Override
			public Object[] getElements(Object parent) {
				return getChildren(parent);
			}

			@Override
			public Object[] getChildren(Object parent) {
				if (parent instanceof Metadata) {
					Iterator<Directory> i = ((Metadata) parent).getDirectoryIterator();
					List<Directory> directories = new ArrayList<Directory>();
					while (i.hasNext()) {
						directories.add(i.next());
					}
					return directories.toArray();
				}
				if (parent instanceof Directory) {
					Iterator<Tag> i = ((Directory) parent).getTagIterator();
					List<Tag> tags = new ArrayList<Tag>();
					while (i.hasNext()) {
						tags.add(i.next());
					}
					Collections.sort(tags, new Comparator<Tag>() {
						@Override
						public int compare(Tag o1, Tag o2) {
							return o1.getTagName().compareTo(o2.getTagName());
						}
					});
					return tags.toArray();
				}
				return new Object[0];
			}

			@Override
			public Object getParent(Object element) {
				if (element instanceof Directory) {
					return meta;
				}
				if (element instanceof Tag) {
					for (Iterator<Directory> i = meta.getDirectoryIterator(); i.hasNext();) {
						Directory next = i.next();
						if (next.getName().equals(((Tag) element).getDirectoryName())) {
							return next;
						}
					}
				}
				return null;
			}

			@Override
			public boolean hasChildren(Object parent) {
				if (parent instanceof Metadata) {
					return ((Metadata) parent).getDirectoryCount() > 0;
				}
				if (parent instanceof Directory) {
					return ((Directory) parent).getTagCount() > 0;

				}
				return false;
			}
		});

		cell1.setLabelProvider(new ColumnLabelProvider() {

			@Override
			public String getText(Object element) {
				if (element instanceof Directory) {
					Directory directory = (Directory) element;
					return directory.getName();
				} else if (element instanceof Tag) {
					Tag tag = (Tag) element;
					return tag.getTagName();
				}
				return "";
			}

			// @Override
			// public void update(ViewerCell cell) {
			// Object element = cell.getElement();
			// if (element instanceof Directory) {
			// Directory directory = (Directory) element;
			// cell.setText(directory.getName());
			// StyleRange style = new StyleRange();
			// style.fontStyle = SWT.BOLD;
			// cell.setStyleRanges(new StyleRange[] { style });
			// } else if (element instanceof Tag) {
			// Tag tag = (Tag) element;
			// cell.setText(tag.getTagName());
			// }
			// }
		});
		cell2.setLabelProvider(new StyledCellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				Object element = cell.getElement();
				if (element instanceof Directory) {
					/* no-op */
				} else if (element instanceof Tag) {
					Tag tag = (Tag) element;
					try {
						cell.setText(tag.getDescription());
					} catch (MetadataException e) {
						cell.setText(e.getMessage());
					}
				}
			}
		});

		viewer.setInput(meta);
		shell.setSize(new Point(700, 400));
		shell.open();
		/* loop */
		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch()) {
				shell.getDisplay().sleep();
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Display d = new Display();
		Shell s = new Shell(d);
		File f = new File("C:\\Users\\ben\\BenVoyage\\Photos\\108_PANA\\JPEG\\P1080690.jpg");
		new ExifViewer(s).open(JpegMetadataReader.readMetadata(f), f);
	}
}
