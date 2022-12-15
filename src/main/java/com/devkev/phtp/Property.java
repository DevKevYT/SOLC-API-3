package com.devkev.phtp;

public class Property {
	
	String key;
	String value;
	
	public static Property accessToken(String value) {
		Property p = new Property();
		p.key = "Authorization";
		p.value = value;
		return p;
	}
	
	public static Property of(String key, String value) {
		Property p = new Property();
		p.key = key;
		p.value = value;
		return p;
	}
}
