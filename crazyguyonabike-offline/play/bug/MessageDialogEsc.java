package bug;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class MessageDialogEsc {
	public static void main(String[] args) {
		String[] buttons = new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL,
				IDialogConstants.CANCEL_LABEL };
		MessageDialog box = new MessageDialog(new Shell(new Display()), "Title", null, "Message",
				MessageDialog.QUESTION_WITH_CANCEL, buttons, 0);
		System.out.println(box.open());
	}
}
