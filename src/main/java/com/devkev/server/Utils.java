package com.devkev.server;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public interface Utils {
	
	public static String convertToUntisDate(Calendar date) {
		return (date.get(Calendar.YEAR) >= 1000 ? String.valueOf(date.get(Calendar.YEAR)) : "1970") +
		        (date.get(Calendar.MONTH)+1 < 10 ? "0" + (date.get(Calendar.MONTH)+1) : date.get(Calendar.MONTH)+1) +
		        (date.get(Calendar.DAY_OF_MONTH) < 10 ? "0" + date.get(Calendar.DAY_OF_MONTH)  : date.get(Calendar.DAY_OF_MONTH));
	}
	
	public static Calendar convertToDate(String untisDate) throws IllegalArgumentException {
		if(untisDate.length() != 8)
			throw new IllegalArgumentException("Invalid date format. Expecting YYYYMMDD");
		
		String year = untisDate.substring(0, 4);
		if(!isWholeNumber(year)) throw new IllegalArgumentException("Invalid year: '" + year + "' for input " + untisDate);
		
		String month = untisDate.substring(4, 6);
		if(!isWholeNumber(month)) throw new IllegalArgumentException("Invalid month: '" + month + "' for input " + untisDate);
		
		String day = untisDate.substring(6, 8);
		if(!isWholeNumber(month)) throw new IllegalArgumentException("Invalid month: '" + month + "' for input " + untisDate);
		
		Calendar instance = Calendar.getInstance();
		instance.set(Integer.valueOf(year), Integer.valueOf(month), Integer.valueOf(day));
		instance.set(Calendar.HOUR_OF_DAY, 0);
		instance.set(Calendar.MINUTE, 0);
		instance.set(Calendar.SECOND, 0);
		instance.set(Calendar.MILLISECOND, 0);
		
		return instance;
	}
	
	final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
	final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); 
	
	public static String toSQLDateTime(Calendar calendar) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, 1);
		Date date = cal.getTime();  
		return dateTimeFormat.format(date);
	}
	
	public static String toSQLDate(Calendar calendar) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, 1);
		Date date = cal.getTime();  
		return dateFormat.format(date);
	}
	
	public static boolean isWholeNumber(String number) {
		for(char c : number.toCharArray()) {
			if(c != '1' && c != '2' && c != '3' && c != '4' && c != '5' && c != '6' && c != '7' && c != '8' && c != '9' && c != '0') return false;
		}
		return true;
	}
	
}
