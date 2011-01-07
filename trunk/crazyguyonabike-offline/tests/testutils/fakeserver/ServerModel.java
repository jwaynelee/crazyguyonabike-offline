package testutils.fakeserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.graphics.Image;

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

		void pageAdded(ServerPage page);

		void photoAdded(ServerPhoto photo);

		void userLoggedIn(String user, String id);

		void userLoggedOut(String user);
	}

	static class ServerJournal {
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

	static class ServerPage {
		private String headline;
		ServerJournal journal;
		private int pageId;
		private List<ServerPhoto> photos = new ArrayList<ServerPhoto>();
		private String text;
		private String title;

		public void addPhoto(ServerPhoto photo) {
			photo.page = this;
			photos.add(photo);
			journal.model.firePhotoAdded(photo);
		}

		public Object getJournal() {
			return journal;
		}

		public int getPageId() {
			return pageId;
		}

		public List<ServerPhoto> getPhotos() {
			return photos;
		}

		public void setHeadline(String headline) {
			this.headline = headline;
		}

		public void setText(String text) {
			this.text = text;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		@Override
		public String toString() {
			return title + " : " + headline;
		}
	}

	static class ServerPhoto {
		private String filename;
		private Image image;
		ServerPage page;
		private int size;

		public Object getPage() {
			return page;
		}

		public void setFilename(String filename) {
			this.filename = filename;
		}

		public void setSize(int size) {
			this.size = size;
		}

		@Override
		public String toString() {
			return filename + " (" + Utils.formatBytes(size) + ")";
		}
	}
}
