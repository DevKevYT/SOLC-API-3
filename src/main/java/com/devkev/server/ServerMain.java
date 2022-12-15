package com.devkev.server;

import java.io.File;
import java.util.Calendar;

import org.jooby.Jooby;

import com.devkev.database.DBConnection;
import com.devkev.database.PhasingDetails;
import com.devkev.phtp.PhTPModelParser;
import com.devkev.phtp.PhTPModelParser.PhTPModel;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {
	
	//TODO set max upload file size
	public static ServerConfiguration SERVER_CONFIG;
	public static DBConnection DB_CON;
	
	public static void main(String[] args) throws Exception {
		
		SERVER_CONFIG = new ServerConfiguration(new File("C:/Users/Philipp/Nextcloud/eclipse_workspaces/eclipse/SOLC-API/debugConfiguration.conf"));
		
//		Jooby.exportConf(new API()).entrySet().forEach((e) -> {
//			System.out.println(e);
//		});
		
		SERVER_CONFIG = new ServerConfiguration(new File("C:/Users/Philipp/Nextcloud/eclipse_workspaces/eclipse/SOLC-API/debugConfiguration.conf"));
		DB_CON = new DBConnection(SERVER_CONFIG);
		Jooby.run(API::new);
//		PhTPModel model = PhTPModelParser.parse(
//				JsonParser.parseString("{\"classId\": 7777,\"version\": 1, \"blockStart\": \"20221010\", \"blockEnd\": \"20221016\", \"weeks\": "
//						+ "[ {\"start\": \"20221010\", \"end\": \"20221014\", "
//						+ "\"days\": [  [ "
//							+ "{\"index\": 0, \"lessonId\": 2}, "
//							+ "{\"index\": 1, \"lessonId\": 10}, "
//							+ "{\"index\": 5, \"lessonId\": 3} ]  ]} ]}").getAsJsonObject());
//		DB_CON.insertPhaseModel(model, Calendar.getInstance(), 2334, "Philipp Gersch", true);
//		PhTPModel m = c.readModel(9999);
//		System.out.println(PhTPModelParser.parse(m));
	}

}
