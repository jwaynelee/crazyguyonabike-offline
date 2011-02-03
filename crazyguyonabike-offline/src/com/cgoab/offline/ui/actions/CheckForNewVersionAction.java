package com.cgoab.offline.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.util.LatestVersionChecker;
import com.cgoab.offline.util.Version;

public class CheckForNewVersionAction extends Action {

	public CheckForNewVersionAction() {
		super("Check for update");
	}

	@Override
	public void run() {
		Version latest = LatestVersionChecker.blockForLatestVersion(Display.getCurrent());
		if (latest == null) {
			MessageDialog.openError(null, "Error checking for new version",
					"Failed to get latest version, check the log for more information.");
		} else {
			Version current = LatestVersionChecker.currentVersion();
			String upgradeMsg = current == null || current.isLessThan(latest) ? "It is recomended that you upgrade"
					: "You do not need to upgrade.";
			MessageDialog.openInformation(null, "Version check", "The latest version is " + latest
					+ ". You are currently using version " + current + ".\n\n" + upgradeMsg);
		}
	}

	public static void main(String[] args) {
		new CheckForNewVersionAction().run();
	}
}
