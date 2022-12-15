package com.devkev.server;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jooby.Jooby;
import org.jooby.Mutant;
import org.jooby.Upload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devkev.phtp.Gateway;
import com.devkev.phtp.PhTPModelParser;
import com.devkev.phtp.PhTPModelParser.PhTPModel;
import com.devkev.phtp.Property;
import com.devkev.phtp.RPCHandler;
import com.devkev.phtp.Response;
import com.devkev.database.PhasingDetails;
import com.devkev.phtp.Exceptions.PhasingNotFound;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class API extends Jooby {
	
	public static final Version VERSION = Version.of("3.0.0");
	private static final String versionJson;
	private static final Logger logger = LoggerFactory.getLogger(API.class);
	
	static {
		versionJson = generateJSON(Codes.CODE_SUCCESS, "{\"displayValue\": \"" + API.VERSION.toString() + 
				"\", \"version\": {\"major\": " + API.VERSION.MAJOR 
				+ ", \"minor\": " + API.VERSION.MINOR 
				+ ", \"patch\": " + API.VERSION.PATCH + "}}");
	}
	
	private static final String generateJSON(int code, String data) {
		return "{\"data\": " + data + ",\"code\": " + code + "}";
	}
	
	//Generiert eine antwort error JSON gemäß des oben genannten Protokolls
	private static final String generateErrorJSON(int code, String errorMessage) {
		return "{\"error\": \"" + errorMessage + "\",\"code\": " + code + "}";
	}
	
	{
		get("/api/version/", (ctx, rsp) -> {
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "GET");
			try {
				rsp.send(versionJson);
			} catch(Exception e) {
				logger.error("Endpoint request failed: " + e);
			}
		});
		
		/**Use this endpoint to retrieve information on the phasing like, when it was uploaded, when it is valid etc.
		 * Use the querystring ?classId=... for different classes. You can get multiple class phases by separating the id's with a ',':
		 * /api/phasing/details?classId=1234,2233,2224 ...*/
		get("/api/phasing/details", (ctx, rsp) -> {
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "GET");
			
			String[] classes = ctx.param("classId").value().split(",");
			ArrayList<Integer> classIds = new ArrayList<>();
			
			for(String s : classes) {
				if(Utils.isWholeNumber(s)) classIds.add(Integer.valueOf(s));
			}
			
			PhasingDetails[] details = ServerMain.DB_CON.getPhasingDetails(classIds.toArray(new Integer[classIds.size()]));
			classIds.clear();
			
			StringBuilder response = new StringBuilder("{");
			for(PhasingDetails d : details) {
				
				StringBuilder classInfo = new StringBuilder("\"" + d.getClassId() + "\": {"
						+ "\"startDate\": " + Utils.convertToUntisDate(d.getStartDate()) + ","
						+ "\"endDate\": " + Utils.convertToUntisDate(d.getEndDate()) + ","
						+ "\"created\": " + d.getUploadedAt().getTimeInMillis() + ","
						+ "\"fileowner\": " + d.getOwnderId() + ","
						+ "\"ownerDisplayName\": \"" + d.getOwnerDisplayName() + "\"},");
				response.append(classInfo);
			}
			
			if(details.length > 0) response.deleteCharAt(response.length()-1);
			response.append("}");
			rsp.send(generateJSON(0, response.toString()));
		});
		
		get("/api/phasing/{class_id}", (ctx, rsp) -> {
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "GET");
			
			if(!Utils.isWholeNumber(ctx.param("class_id").value())) {
				rsp.send(generateErrorJSON(Codes.CODE_PHASING_NOT_FOUND, "Invalid class id"));
				return;
			}
			
			try {
				PhTPModel model = PhTPModelParser.parseFromDatabase(ServerMain.DB_CON, Integer.valueOf(ctx.param("class_id").value()));
				rsp.send(generateJSON(0, PhTPModelParser.parse(model)));
			
			} catch(PhasingNotFound notFound) {
				rsp.send(generateErrorJSON(Codes.CODE_PHASING_NOT_FOUND, "Phasing for class not found"));
			
			} catch(SQLException sql) {
				rsp.send(generateErrorJSON(Codes.CODE_UNKNOWN_ERROR, "Something went really wrong while reading from the database"));
				sql.printStackTrace();
			}
		});
		
		/**Provides an option to upload an excel sheet for a specific class. This sheet is then converted into a "phasing" data structure and saved in the database.
		 * For security reasons, successfully calling this function causes the client to log out with the given credentials.
		 * Needs to have the following headers set:
		 * - JSESSIONID (Valid session)
		 * - Authentication (Bearer token)*/
		post("/api/phasing/uplad/", (ctx, rsp) -> {
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "GET");
			
			try {
				JsonObject phasing = JsonParser.parseString(ctx.body().value()).getAsJsonObject();
				PhTPModel model = PhTPModelParser.parse(phasing);
				
				Mutant m_sessionId = ctx.header("JSESSIONID");
				if(!m_sessionId.isSet()) {
					
				}
				Mutant m_token = ctx.header("Authentication");
				if(!m_token.isSet()) {
					
				}
				
				
				final String sessionId = m_sessionId.value();
				final String token = m_token.toString();
				
				//Schritt 1: Verifiziere die SessionID:
				Calendar calendar = Calendar.getInstance();
				String testDate = calendar.get(Calendar.YEAR) + "" + 
						(calendar.get(Calendar.MONTH) <= 9 ? "0" + (calendar.get(Calendar.MONTH)+1) : (calendar.get(Calendar.MONTH)+1))
						+ (calendar.get(Calendar.DAY_OF_MONTH) <= 9 ? "0" + calendar.get(Calendar.DAY_OF_MONTH) : calendar.get(Calendar.DAY_OF_MONTH));
				
				RPCHandler response = RPCHandler.sendRequest("https://hepta.webuntis.com/WebUntis/jsonrpc.do?school=bbs1-mainz", "SOLC-API", "getTimetable", 
						"{\"startDate\":\"" + testDate + "\",\"endDate\":\"" + testDate + "\" }", 
						Property.of("Cookie", "JSESSIONID=" + sessionId));
				
				if(!response.success()) {
					//arg1.error("Missing API payload. API down? (Http: " + response.httpStatus + ", error message: " + response.errorMessage + ", error code: " + response.errorCode + ")");
					return;
				
				} else {
					if(response.getErrorCode() == -8520) {
						//arg1.error("Session authentification failed");
						return;
					} 
				}
				
				try {
					RPCHandler.sendRequest("https://hepta.webuntis.com/WebUntis/jsonrpc.do?school=bbs1-mainz", "SOLC-API", "logout", null, 
							Property.of("Cookie", "JSESSIONID=" + sessionId));
				} catch(Exception e) {
					//Main.logger.log("Failed to log user out");
					
				}
				
				//Schritt 2: Verifiziere Rechte
				Response<JsonObject> data = Gateway.GET("https://hepta.webuntis.com/WebUntis/api/rest/view/v1/app/data", Property.of("Authorization", "Bearer " + token));
				
				if(!data.containsPayload()) {
					
					//arg1.error("Missing API payload. API down? (Http: " + data.httpStatus + ", error message: " + data.errorMessage + ", error code: " + data.errorCode + ")");
					return;
				} else {
					if(data.httpStatus == 401) {
						//arg1.error("Invalid Token");
						return;
						
					} else if(data.httpStatus != 200) {
						//arg1.error("http error: " + data.httpStatus);
						return;
					}
					if(data.getResponseData().getAsJsonObject("user").getAsJsonObject("person") != null) {
						int uploaderId = data.getResponseData().getAsJsonObject("user").getAsJsonObject("person").getAsJsonPrimitive("id").getAsInt();
						String uploaderDisplayName = data.getResponseData().getAsJsonObject("user").getAsJsonPrimitive("name").getAsString();
						
						//Überprüfe Berechtigung
						if(data.getResponseData().getAsJsonObject("user").getAsJsonArray("roles") != null) {
							for(JsonElement e : data.getResponseData().getAsJsonObject("user").getAsJsonArray("roles")) {
								if(e.getAsString().equals("TEACHER")) {
									ServerMain.DB_CON.insertPhaseModel(model, Calendar.getInstance(), uploaderId, uploaderDisplayName, true);
								}
								return;
							}
						}
						//arg1.error("No teacher permission");
						return;
					}
				}
				
				//arg1.error("Unknown Error: " + data.httpStatus);
				
				
			} catch(Exception e) {
				//SLogger.ERROR.log("Something bad happened for endpoint: /api/triumph/manifest");
				//e.printStackTrace();
				throw e;
			}
		});
		
//		 err((req, rsp, err) -> {
//			err.get
//		 });
		
		final int MAX_CELL_ENTRIES = 500;
		
		//application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
		/**Returns mapped colors of an excel sheet.
		 * The binary */
		post("/api/getcolor", (ctx, rsp) -> {
			ctx.accepts("multipart/form-data");
			
			rsp.header("content-type", "text/json; charset=utf-8");
			rsp.header("Access-Control-Allow-Origin", "*");
			rsp.header("Access-Control-Allow-Methods", "POST");
			
			if(!ctx.ifFile("sheet").isPresent()) {
				rsp.send(generateErrorJSON(Codes.CODE_MISSING_FILE_UPLOAD, "Missing excel file named 'sheet' as upload"));
				return;
			}
			
			Upload file = ctx.file("sheet");
			
			try {
				StringBuilder json = new StringBuilder("{\"data\":[");
				Workbook workbook = new XSSFWorkbook(file.file());
				Sheet sheet = workbook.getSheetAt(0);
				
	    		int i = 0;
	    		int cellEntries = 0;
	    		
	    		main: for (Row row : sheet) {
	    		    for (Cell cell : row) {
	    		    	Color fillColor = cell.getCellStyle().getFillForegroundColorColor();
	    		    	if(fillColor != null) {
		    		    	String hex = ((XSSFColor) fillColor).getARGBHex().substring(1);
		    		    	String cellEntry = "{\"x\":" + cell.getColumnIndex() + ",\"y\":" + cell.getRowIndex() + ",";
		    		    	String colorEntry = "\"c\":{";
		    		    	
		    		    	int colorIndex = 0;
		    		    	for(int rgb : hex2Rgb(hex)) {
		    		    		if(colorIndex == 0) colorEntry += "\"r\":" + rgb + ",";
		    		    		else if(colorIndex == 1) colorEntry += "\"g\":" + rgb + ",";
		    		    		else if(colorIndex == 2) colorEntry += "\"b\":" + rgb;
		    		    		colorIndex++;
		    		    	}
		    		    	colorEntry += "}";
		    		    	
		    		    	cellEntry += colorEntry + "},";
		    		    	json.append(cellEntry);
		    		    	cellEntries++;
		    		    	
		    		    	if(cellEntries >= MAX_CELL_ENTRIES) {
		    		    		rsp.send(generateErrorJSON(Codes.CODE_EXCEEDED_MAX_EXCEL_SIZE, "Exceeded max colored cell entries. Excel too large"));
		    		    		break main;
		    		    	}
	    		    	}
	    		    }
	    		    i++;
	    		}
	    		if(i > 0) json.deleteCharAt(json.length()-1);
	    		json.append("]}");
	    		
	    		workbook.close();
	    		rsp.send(json.toString().trim());
			} catch(Exception e) {
				rsp.send(generateErrorJSON(Codes.CODE_UNKNOWN_ERROR, "Unknown error while extracting cell colors: " + e.getMessage()));
			}
			
			file.close();
		});
		
	}
	
	public int[] hex2Rgb(String colorStr) {
	    return new int[] {
	            Integer.valueOf(colorStr.substring(1, 3), 16 ),
	            Integer.valueOf(colorStr.substring(3, 5), 16 ),
	            Integer.valueOf(colorStr.substring(5, 7), 16 )
	    };
	}
}
