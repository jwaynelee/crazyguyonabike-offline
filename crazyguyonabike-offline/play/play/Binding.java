package play;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.PojoProperties;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class Binding {
	public static void main(String[] args) {
		final Display d = new Display();
		Shell s = new Shell(d);
		s.setLayout(new FillLayout());
		Text text = new Text(s, SWT.SINGLE);
		Model m = new Model();

		s.open();

		DataBindingContext bindingContext = new DataBindingContext();
		IObservableValue myModel = PojoProperties.value(Model.class, "firstName").observe(m);
		IObservableValue myWidget = WidgetProperties.text(SWT.Modify).observe(text);
		bindingContext.bindValue(myWidget, myModel);

		// loop
		s.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent arg0) {
				d.dispose();
			}
		});
		while (!d.isDisposed()) {
			if (!d.readAndDispatch()) {
				d.sleep();
			}
		}
	}

	public static class Model {
		String name;

		public void setName(String name) {
			System.out.println(name);
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}
}
