package com.devkev.phtp;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class RPCHandler {
	
	public static final String USER_AGENT = "Mozilla/5.0";
	
	public final String statusMessage;
	public final int httpStatus;
	
	private String appId = "unknown";
	private String rpcVersion = "2.0";
	private JsonObject payload;
	private int errorCode = -9999;
	
	private RPCHandler(int httpStatus, String statusMessage) {
		this.httpStatus = httpStatus;
		this.statusMessage = statusMessage;
	}
	
	/**Usually https://hepta.webuntis.com/WebUntis/jsonrpc.do?school=SCHOOL_ID
	 * Maybe wrap this function later
	 * Body needs to be in a special format*/
	public static RPCHandler sendRequest(String url, String appName, String method, String parameterJson, Property ... customHeaders) {
		if(parameterJson == null) parameterJson = "";
		
		URL obj;
		HttpURLConnection httpURLConnection;
		try {
			obj = new URL(url);
			httpURLConnection = (HttpURLConnection) obj.openConnection();
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.setRequestProperty("User-Agent", USER_AGENT);
			httpURLConnection.setRequestProperty("Content-Type", "application/json");
			
			for(Property p : customHeaders) 
				httpURLConnection.setRequestProperty(p.key, p.value);
			
			httpURLConnection.setDoOutput(true);
		} catch (Exception e) {
			return new RPCHandler(404, "Failed to connect to " + url + " (" + e.getLocalizedMessage() + ")");
		}
		
		String rpcBody = parameterJson != "" ? "{\"id\": \"" + appName + "\", \"method\": \"" + method + "\", \"params\": " + parameterJson + ", \"jsonrpc\": 2.0}" 
				: "{\"id\": \"" + appName + "\", \"method\": \"" + method + "\", \"jsonrpc\": 2.0}" ;
		try {
			OutputStream os = httpURLConnection.getOutputStream();
			os.write(rpcBody.getBytes());
			os.flush();
			os.close();
			httpURLConnection.connect();
		} catch(Exception e) {
			return new RPCHandler(404, "Failed to send POST request to " + url + " (" + e.getLocalizedMessage() + ")");
		}

		String inputLine;
		StringBuilder responseCollector = new StringBuilder();
		int responseCode = 500;
		
		try {
			responseCode = httpURLConnection.getResponseCode();
			InputStream stream;
			if(responseCode >= 200 && responseCode < 400) stream = httpURLConnection.getInputStream();
			else stream = httpURLConnection.getErrorStream();
			
			BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
			while ((inputLine = in.readLine()) != null) responseCollector.append(inputLine);
			in.close();
			
			JsonObject response;
			try {
				response = (JsonObject) JsonParser.parseString(responseCollector.toString());
			} catch(Exception e) {
				return new RPCHandler(500, "Unable to create JsonData with content: " + responseCollector.toString());
			}
			
			if(responseCode != 200) {
				return new RPCHandler(responseCode, "Unknown Error");
			}
			
			JsonPrimitive appId = response.getAsJsonPrimitive("id");
			JsonPrimitive rpcVersion = response.getAsJsonPrimitive("jsonrpc");
			JsonObject errorMessage = response.getAsJsonObject("error");
			
			if(errorMessage != null) {
				RPCHandler error = new RPCHandler(responseCode, errorMessage.getAsJsonObject().getAsJsonPrimitive("message").getAsString());
				error.errorCode = errorMessage.getAsJsonObject().getAsJsonPrimitive("code").getAsInt();
				error.rpcVersion = rpcVersion != null ? rpcVersion.getAsString() : "unknown";
				error.appId = rpcVersion != null ? appId.getAsString() : "unknown";
				return error;
			}
			
			RPCHandler success = new RPCHandler(responseCode, "success");
			success.payload = response.getAsJsonObject("result");
			success.rpcVersion = rpcVersion != null ? rpcVersion.getAsString() : "unknown";
			success.appId = rpcVersion != null ? appId.getAsString() : "unknown";
			success.errorCode = 0;
			return success;
		} catch(Exception e) {
			e.printStackTrace();
			return new RPCHandler(responseCode, "Failed to handle response from " + url + " (" + e.getLocalizedMessage() + ")");
		}
	}
	
	
	public int getErrorCode() {
		return errorCode;
	}
	
	public String getAppId() {
		return appId;
	}
	
	public String getRPCVersion() {
		return rpcVersion;
	}
	
	public JsonObject getPayload() {
		return payload;
	}
	
	public boolean success() {
		return httpStatus != 200 && errorCode != 0;
	}
}
