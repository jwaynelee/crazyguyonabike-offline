package play;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferencePage;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.widgets.Display;

public class PreferencesPlag {
	public static void main(String[] args) {
		Display d = new Display();
		ApplicationWindow window = new ApplicationWindow(null) {
			{
				setBlockOnOpen(true);
				addMenuBar();
			}

			@Override
			protected MenuManager createMenuManager() {
				MenuManager root = new MenuManager();
				MenuManager file = new MenuManager("file");
				root.add(file);
				file.add(new ShowPreferences());
				return root;
			}
		};
		window.open();
		d.dispose();
	}

	private static class ShowPreferences extends Action {
		public ShowPreferences() {
			super("Preferences");
		}

		@Override
		public void run() {
			IPreferencePage page = new MyPreferencePage();
			page.setTitle("BLAH");
			PreferenceManager mgr = new PreferenceManager();
			PreferenceNode node = new PreferenceNode("foo", page);// "foo.bar.car",
																	// "bark",
																	// null,
																	// MyPreferencePage.class.getName());
			mgr.addToRoot(node);
			PreferenceDialog dialog = new PreferenceDialog(null, mgr);
			dialog.create();
			dialog.setMessage("FART");
			dialog.open();
		}
	}

	public static class MyPreferencePage extends FieldEditorPreferencePage {

		@Override
		protected void createFieldEditors() {
			BooleanFieldEditor formatOnSave = new BooleanFieldEditor("xyz", "&Format before saving",
					getFieldEditorParent());
			addField(formatOnSave);
		}
	}
}
