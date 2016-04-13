package com.blazeloader.jarjar.console;

import java.util.HashMap;

public class Options extends HashMap<String, String> {
	
	public void readOption(String line) {
		String[] splitten = line.split("=");
		if (splitten.length != 2) return;
		splitten[0] = splitten[0].trim();
		splitten[1] = splitten[1].trim();
		put(splitten[0].toLowerCase(), splitten[1]);
	}
	
	public int getInt(String key) {
		return containsKey(key) ? Integer.parseInt(get(key)) : 0;
	}
	

	public boolean getBool(String key) {
		return containsKey(key) ? Boolean.parseBoolean(get(key)) : false;
	}
}
