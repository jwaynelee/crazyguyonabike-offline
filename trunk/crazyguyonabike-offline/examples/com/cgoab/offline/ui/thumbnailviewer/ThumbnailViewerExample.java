package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.drew.metadata.Metadata;

public class ThumbnailViewerExample {
	public static void main(String[] args) {
		Display d = new Display();
		Shell s = new Shell(d);
		s.setLayout(new FillLayout());
		s.setSize(800, 300);
		final ThumbnailViewer thumbnailViewer = new ThumbnailViewer(s);
		final List<File> files = new ArrayList<File>();
		files.add(new File("C:\\Users\\ben\\BenVoyage\\Photos\\108_PANA\\JPEG\\P1080690.jpg"));
		thumbnailViewer.setContentProvider(new ThumbnailViewerContentProvider() {

			@Override
			public void dispose() {
			}

			@Override
			public void inputChanges(ThumbnailViewer viewer, Object oldInput, Object newInput) {
			}

			@Override
			public Object[] getThumbnails(Object input) {
				return files.toArray();
			}
		});
		thumbnailViewer.setLabelProvider(new ThumbnailViewerLabelProvider() {

			@Override
			public String getImageText(Object thumbnail) {
				return getImageFile(thumbnail).getName();
			}

			@Override
			public File getImageFile(Object thumbnail) {
				return (File) thumbnail;
			}

			@Override
			public int getOpacity(Object o) {
				return 255;
			}

			@Override
			public Image getOverlay(Object o) {
				return null;
			}
		});
		thumbnailViewer.addEventListener(new ThumbnailViewerEventListener() {

			@Override
			public boolean itemFailedToLoad(Object image, Throwable exception) {
				return false;
			}

			@Override
			public void itemsRemoved(Object[] selection) {
				files.removeAll(Arrays.asList(selection));
				thumbnailViewer.refresh();

				// thumbnailViewer.remove(selection); // more efficient than
				// refresh
				updateInfo();
			}

			@Override
			public void itemsMoved(Object[] selection, int insertionPoint) {
				Collection<File> selectionList = new ArrayList<File>(selection.length);
				for (Object file : selection) {
					selectionList.add((File) file);
				}

				// walk up to the insertion point, counting how many selected
				// items we find
				int found = 0;
				for (int i = 0; i < insertionPoint; ++i) {
					if (selectionList.contains(files.get(i))) {
						found++;
					}
				}

				// remove the selection
				files.removeAll(selectionList);

				// re-insert the selection at adjusted index
				files.addAll(insertionPoint - found, selectionList);
				thumbnailViewer.refresh();
				updateInfo();
			}

			@Override
			public void itemsAdded(File[] newItems, int insertionPoint) {
				for (File file : newItems) {
					if (!files.contains(file)) {
						files.add(insertionPoint++, file);
					} else {
						System.out.println("Duplicate: " + file);
					}
				}
				thumbnailViewer.refresh();
				updateInfo();
			}

			private void updateInfo() {
				String str = "";
				for (File file : files) {
					str += file.getName() + " - ";
				}
				System.out.println(str);
			}
		});
		thumbnailViewer.setInput(files);

		s.open();
		while (!s.isDisposed()) {
			if (!d.readAndDispatch()) {
				d.sleep();
			}
		}
		d.dispose();
	}
}
