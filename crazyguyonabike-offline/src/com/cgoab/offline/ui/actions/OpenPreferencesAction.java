package com.cgoab.offline.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FontFieldEditor;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.preference.StringFieldEditor;

import com.cgoab.offline.ui.PreferenceUtils;

public class OpenPreferencesAction extends Action {

	public OpenPreferencesAction() {
		super("Open preferences");
	}

	@Override
	public void run() {
		PreferenceManager mgr = new PreferenceManager();
		mgr.addToRoot(new PreferenceNode("editor", new EditorPreferencesPage()));
		// mgr.addToRoot(new PreferenceNode("internal", new
		// InternalPreferencePage()));
		mgr.addToRoot(new PreferenceNode("magick", new MagickPreferencePage()));
		mgr.addToRoot(new PreferenceNode("updates", new UpdatesPreferencePage()));
		PreferenceDialog dialog = new PreferenceDialog(null, mgr);
		dialog.setPreferenceStore(PreferenceUtils.getStore());
		dialog.create();
		dialog.open();
	}

	public static class EditorPreferencesPage extends FieldEditorPreferencePage {

		public EditorPreferencesPage() {
			super(GRID);
			setTitle("Editor");
			setMessage("Editor");
		}

		@Override
		protected void createFieldEditors() {
			FontFieldEditor font = new FontFieldEditor(PreferenceUtils.FONT, "Editor font", getFieldEditorParent());
			addField(font);
		}
	}

	public static class MagickPreferencePage extends FieldEditorPreferencePage {

		public MagickPreferencePage() {
			super(GRID);
			setMessage("ImageMagick");
			setTitle("ImageMagick");
		}

		@Override
		protected void createFieldEditors() {
			StringFieldEditor magickPath = new StringFieldEditor(PreferenceUtils.MAGICK_PATH, "ImageMagick path",
					getFieldEditorParent());
			IntegerFieldEditor sizePath = new IntegerFieldEditor(PreferenceUtils.RESIZE_DIMENSIONS,
					"Resized photo size", getFieldEditorParent(), 4);
			sizePath.setValidRange(500, 2000);
			IntegerFieldEditor jpegQuality = new IntegerFieldEditor(PreferenceUtils.RESIZE_QUALITY, "JPEG quality",
					getFieldEditorParent(), 3);
			jpegQuality.setValidRange(20, 100);
			addField(magickPath);
			addField(sizePath);
			addField(jpegQuality);
		}
	}

	public static class UpdatesPreferencePage extends FieldEditorPreferencePage {

		public UpdatesPreferencePage() {
			super(GRID);
			setMessage("Updates");
			setTitle("Updates");
		}

		@Override
		protected void createFieldEditors() {
			BooleanFieldEditor checkForUpdates = new BooleanFieldEditor(PreferenceUtils.CHECK_FOR_UPDATES,
					"Automatically check for updates?", getFieldEditorParent());
			addField(checkForUpdates);
		}
	}

	public static class InternalPreferencePage extends FieldEditorPreferencePage {

		public InternalPreferencePage() {
			super(GRID);
			setMessage("Internal");
			setTitle("Internal");
		}

		@Override
		protected void createFieldEditors() {
			BooleanFieldEditor useMockClient = new BooleanFieldEditor(PreferenceUtils.USE_MOCK_CLIENT,
					"Use mock client (testing only)", getFieldEditorParent());
			addField(useMockClient);
		}
	}
}
