package com.cgoab.offline.client.web;

import java.util.Map;

public class LoginBinder extends AbstractFormBinder {

	public LoginBinder() {
		super("LoginPage");
		registerUsedProperty("username");
		registerUsedProperty("password");
		registerUsedProperty("persistent");
		registerUnusedProperty("command");
		registerUnusedProperty("button");
		registerUnusedProperty("o");
	}

	public Map<String, Object> bind(String username, String password) {
		ParamaterBuilder collector = newCollector();
		collector.put("username", username);
		collector.put("password", password);
		collector.put("persistent", "1");
		return collector.getMap();
	}

}
