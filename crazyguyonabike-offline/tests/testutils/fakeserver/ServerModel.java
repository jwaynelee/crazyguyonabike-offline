package testutils.fakeserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.graphics.Image;

import com.cgoab.offline.model.Page.HeadingStyle;
import com.cgoab.offline.util.Assert;
import com.cgoab.offline.util.Utils;

public class ServerModel {
	private final Map<Integer, ServerJournal> journals = new HashMap<Integer, ServerJournal>();
	private final List<ModelListener> listeners = new ArrayList<ModelListener>();
	private int nextUserId;
	private final List<LoggedInUser> users = new ArrayList<LoggedInUser>();

	public void addJournal(ServerJournal journal) {
		Assert.isTrue(journal.getDocId() > 0);
		journal.model = this;
		journals.put(journal.getDocId(), journal);
		fireJournalAdded(journal);
	}

	public void addListener(ModelListener listener) {
		listeners.add(listener);
	}

	private void fireJournalAdded(ServerJournal journal) {
		for (ModelListener listener : listeners) {
			listener.journalAdded(journal);
		}
	}

	private void fireLoggedIn(String user, String id) {
		for (ModelListener listener : listeners) {
			listener.userLoggedIn(user, id);
		}
	}

	private void fireLoggedOut(String user) {
		for (ModelListener listener : listeners) {
			listener.userLoggedOut(user);
		}
	}

	void firePageAdded(ServerPage page) {
		for (ModelListener listener : listeners) {
			listener.pageAdded(page);
		}
	}

	public void firePageRemoved(ServerPage page) {
		for (ModelListener listener : listeners) {
			listener.pageRemoved(page);
		}
	}

	void firePhotoAdded(ServerPhoto photo) {
		for (ModelListener listener : listeners) {
			listener.photoAdded(photo);
		}
	}

	public ServerJournal getJournal(int id) {
		return journals.get(id);
	}

	public Collection<ServerJournal> getJournals() {
		return journals.values();
	}

	public Collection<LoggedInUser> getLoggedInUsers() {
		return new ArrayList<ServerModel.LoggedInUser>(users);
	}

	public ServerPage getPage(int pageId) {
		for (ServerJournal journal : journals.values()) {
			for (ServerPage page : journal.getPages()) {
				if (page.getPageId() == pageId) {
					return page;
				}
			}
		}
		return null;
	}

	public boolean isValidUserId(String id) {
		for (LoggedInUser user : users) {
			if (user.id.equals(id)) {
				return true;
			}
		}
		return false;
	}

	public String loggedIn(String user) {
		String id = "" + (nextUserId++) + "-" + System.currentTimeMillis();
		users.add(new LoggedInUser(user, id));
		fireLoggedIn(user, id);
		return id;
	}

	public void loggedOut(String user) {
		users.remove(user);
		fireLoggedOut(user);
	}

	static class LoggedInUser {
		String user, id;

		public LoggedInUser(String user, String id) {
			this.user = user;
			this.id = id;
		}

		@Override
		public String toString() {
			return user + " (" + id + ")";
		}
	}

	public interface ModelListener {
		void journalAdded(ServerJournal journal);

		void pageRemoved(ServerPage page);

		void pageAdded(ServerPage page);

		void photoAdded(ServerPhoto photo);

		void userLoggedIn(String user, String id);

		void userLoggedOut(String user);
	}

	public static class ServerJournal {
		private int docId;
		ServerModel model;
		private String name;
		private List<ServerPage> pages = new ArrayList<ServerPage>();

		public ServerJournal(String name, int i) {
			this.name = name;
			docId = i;
		}

		public int addPage(ServerPage page) {
			page.journal = this;
			pages.add(page);
			page.pageId = pages.size();
			model.firePageAdded(page);
			return page.pageId;
		}

		public void deletePage(ServerPage page) {
			pages.remove(page);
			model.firePageRemoved(page);
		}

		public int getDocId() {
			return docId;
		}

		public String getName() {
			return name;
		}

		public List<ServerPage> getPages() {
			return pages;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public static class ServerPage {
		ServerJournal journal;
		private int pageId;
		private List<ServerPhoto> photos = new ArrayList<ServerPhoto>();
		private Map<String, String> params;

		public ServerPage(Map<String, String> params) {
			this.params = params;
		}

		public void addPhoto(ServerPhoto photo) {
			photo.page = this;
			photos.add(photo);
			journal.model.firePhotoAdded(photo);
		}

		public ServerJournal getJournal() {
			return journal;
		}

		public int getPageId() {
			return pageId;
		}

		public List<ServerPhoto> getPhotos() {
			return photos;
		}

		public int getIndent() {
			return Integer.parseInt(params.get("toc_level"));
		}

		public boolean isItalic() {
			return "on".equals(params.get("toc_heading_italic"));
		}

		public String getDate() {
			return params.get("date");
		}

		public int getDistance() {
			return Integer.parseInt(params.get("distance"));
		}

		public String getTitle() {
			return params.get("title");
		}

		public String getHeadline() {
			return params.get("headline");
		}

		public String getText() {
			return params.get("text");
		}

		public boolean isBold() {
			return "on".equals(params.get("toc_heading_bold"));
		}

		@Override
		public String toString() {
			return getTitle() + " : " + getHeadline();
		}

		public HeadingStyle getHeadingStyle() {
			return HeadingStyle.valueOf(params.get("toc_heading_size"));
		}
	}

	public static class ServerPhoto {
		private String filename;
		private Image image;
		ServerPage page;
		private long size;

		public Object getPage() {
			return page;
		}

		public void setFilename(String filename) {
			this.filename = filename;
		}

		public void setSize(long size) {
			this.size = size;
		}

		public long getSize() {
			return size;
		}

		@Override
		public String toString() {
			return filename + " (" + Utils.formatBytes(size) + ")";
		}

		public Object getFilename() {
			return filename;
		}
	}

}
