package com.devkev.server;

import java.io.File;

import com.sn1pe2win.config.dataflow.Node;
import com.sn1pe2win.config.dataflow.Parser;
import com.sn1pe2win.config.dataflow.Variable;

/**Diese Klasse managed die Serverkonfigurationsdatei
 * Da die Konfiguration f�r ein Programm einzigartig ist ist diese Klasse ein Singleton und Ressourcen sind static*/
public class ServerConfiguration {
	
	private Node node;
	
	//Datenbank
	public final String dbSchemaName;
	public final String dbUsername;
	public final String dbPassword;
	public final int dbPort;
	public final String dbAddress;
	
	//Logging
	public final String logLevel;
	
	//phasing
	public final int maxLessonsPerDay;
	
	ServerConfiguration(File file) {
		node = Parser.parse(file);
		
		dbUsername = getMandatoryField("db_username").getAsString();
		dbPassword = getMandatoryField("db_password").getAsString();
		dbPort = getOptionalField("db_port", 3306);
		dbAddress = getOptionalField("db_server", "localhost");
		dbSchemaName = getOptionalField("db_schema_name", "solc_api");
		logLevel = getOptionalField("log_level", "ALL"); //
		maxLessonsPerDay = getOptionalField("ph_max_lessons_per_day", 50);
	}
	
	private String getOptionalField(String fieldName, String fallback) {
		Variable var = node.get(fieldName);
		if(var.isUnknown()) return fallback;
		return var.getAsString();
	}
	
	private int getOptionalField(String fieldName, int fallback) {
		Variable var = node.get(fieldName);
		if(var.isUnknown() || !var.isNumber()) return fallback;
		return var.getAsInt();
	}
	
	private Variable getMandatoryField(String fieldName) {
		Variable var = node.get(fieldName);
		if(var.isUnknown()) throw new IllegalArgumentException("Mandatory value missing or wrong format in configuration file: " + fieldName);
		return var;
	}
}
