package com.cgoab.offline.ui.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.eclipse.swt.widgets.Display;

public class UIThreadCallbackMarsheller {

	@SuppressWarnings("unchecked")
	public static <T> T wrap(T target, Display  display) {
		// TODO verify all interface methods return null...
		return (T) Proxy.newProxyInstance(UIThreadCallbackMarsheller.class.getClassLoader(), target.getClass()
				.getInterfaces(), new Handler(target, display));
	}

	private static class Handler implements InvocationHandler {
		private Object target;
		private Display display;

		public Handler(Object target, Display display) {
			this.target = target;
			this.display = display;
		}

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
			display.asyncExec(new Runnable() {
				@Override
				public void run() {
					try {
						method.invoke(target, args);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			});
			return null;
		}
	}
}
