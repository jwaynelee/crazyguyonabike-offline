package com.cgoab.offline.ui;

import java.lang.reflect.Method;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.joda.time.LocalDate;

/**
 * @TODO replace with eclips binding framework
 */
abstract class PropertyBinder implements Listener {

	private final Control c;
	private final Method property;

	public PropertyBinder(Control c, Method m) {
		this.c = c;
		this.property = m;
	}

	private Object convert(String value, Class<?> target) {
		if (target == String.class) {
			return value;
		}
		if (target.isEnum()) {
			return Enum.valueOf((Class<? extends Enum>) target, value.toUpperCase());
		} else if (target.isPrimitive()) {
			boolean empty = value == null || "".equals(value.trim());
			if (target == int.class) {
				return empty ? 0 : Integer.valueOf(value);
			}
			if (target == float.class) {
				return empty ? 0 : Float.valueOf(value);
			}
			if (target == long.class) {
				return empty ? 0 : Long.valueOf(value);
			}
			if (target == double.class) {
				return empty ? 0 : Double.valueOf(value);
			}
		}
		throw new IllegalArgumentException("Cannot convert to " + target);
	}

	protected abstract Object getTarget();

	@Override
	public void handleEvent(Event event) {
		Object target = getTarget();
		if (target == null) {
			return;
		}
		/* copy from control to model */
		try {
			if (c instanceof Text) {
				String value = ((Text) c).getText();
				property.invoke(target, convert(value, property.getParameterTypes()[0]));
			} else if (c instanceof Button) {
				property.invoke(target, ((Button) c).getSelection());
			} else if (c instanceof Combo) {
				property.invoke(target, convert(((Combo) c).getText(), property.getParameterTypes()[0]));
			} else if (c instanceof DateTime) {
				DateTime dt = (DateTime) c;
				property.invoke(target, new LocalDate(dt.getYear(), dt.getMonth() + 1, dt.getDay()));
			} else {
				throw new IllegalArgumentException("Cannot bind control " + c.getClass().getName());
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}