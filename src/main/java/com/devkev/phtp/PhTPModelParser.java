package com.devkev.phtp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.devkev.database.DBConnection;
import com.devkev.database.Phases;
import com.devkev.phtp.Exceptions.PhasingNotFound;
import com.devkev.server.ServerMain;
import com.devkev.server.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public interface PhTPModelParser {
	
	public class PhTPDate {
		
		Calendar date;
		
		public PhTPDate() {}
		
		public PhTPDate(Date date) {
			this.date = Calendar.getInstance();
			this.date.setTimeInMillis(date.getTime());
		}
		
		public static PhTPDate parse(String date) {
			PhTPDate d = new PhTPDate();
			d.date = Utils.convertToDate(date);
			return d;
		}
		
		public Calendar getDate() {
			return date;
		}
	}
	
	public class PhTPLesson {
		private Phases firstPhase;
		private Phases secondPhase;
		private int lessonId;
		private int index;
		
		public boolean isLesson() {
			return lessonId != 0;
		}
		
		public Phases getFirstPhase() {
			return firstPhase;
		}
		
		public Phases getSecondPhase() {
			return secondPhase;
		}
		
		public int getLessonId() {
			return lessonId;
		}
		
		public int getIndex() {
			return index;
		}
	}
	
	public class PhTPDay {
		
		private ArrayList<PhTPLesson> lessons = new ArrayList<>();
		private int index = 0;
		
		public ArrayList<PhTPLesson> getLessons() {
			return lessons;
		}
		
		public int getDayIndex() {
			return index;
		}
	}
	
	public class PhTPWeek {
		
		private ArrayList<PhTPDay> days = new ArrayList<>();
		
		private PhTPDate start;
		private PhTPDate end;
		private int index = 0;
		
		public PhTPDate getWeekStart() {
			return start;
		}
		
		public PhTPDate getWeekEnd() {
			return end;
		}
		
		public ArrayList<PhTPDay> getDays() {
			return days;
		}
		
		public int getIndex() {
			return index;
		}
	}
	
	public class PhTPModel {
		private int classId;
		private String version;
		private PhTPDate blockStart;
		private PhTPDate blockEnd;
		private PhTPWeek[] weeks;
		
		//Ensures, that only parsed and valid models are handled
		private PhTPModel() {}
		
		public int getClassId() {
			return classId;
		}
		
		public String getVersion() {
			return version;
		}
		
		public PhTPDate getBlockStart() {
			return blockStart;
		}
		
		public PhTPDate getBlockEnd() {
			return blockEnd;
		}
		
		public PhTPWeek[] getWeeks() {
			return weeks;
		}
	}
	
	//TODO better error handling
	public static PhTPModel parseFromDatabase(DBConnection database, int classId) throws SQLException, PhasingNotFound {
		ResultSet result = database.query("SELECT * FROM phasing WHERE class_id = " + classId);
		
		if(!result.next()) 
			throw new PhasingNotFound("Phasing for class with id " + classId + " not found");
		
		PhTPModel model = new PhTPModel();
		model.classId = classId;
		model.version = "1";
		model.blockStart = new PhTPDate(result.getDate("start_date"));
		model.blockEnd = new PhTPDate(result.getDate("end_date"));
		
		ResultSet lessons = database.query("SELECT * FROM lesson WHERE class_id = " + classId);
		ResultSet weekDefinitions = database.query("SELECT * FROM week_definition WHERE class_id = " + classId);
		
		ArrayList<PhTPWeek> weekTemp = new ArrayList<>();
		
		while(weekDefinitions.next()) {
			PhTPWeek week = new PhTPWeek();
			week.start = new PhTPDate(weekDefinitions.getDate("start_date"));
			week.end = new PhTPDate(weekDefinitions.getDate("end_date"));
			week.index = weekDefinitions.getInt("week_index");
			weekTemp.add(week);
		}
		
		while(lessons.next()) {
			PhTPLesson lesson = new PhTPLesson(); //Now, put the lesson to the day and week where it belongs. Add weeks and days if needed
			lesson.firstPhase = Phases.parse(lessons.getShort("first_phase"));
			lesson.secondPhase = Phases.parse(lessons.getShort("second_phase"));
			lesson.index = lessons.getInt("hour_index");
			lesson.lessonId = lessons.getInt("lesson_id");
			
			int weekIndex = lessons.getInt("week_index");
			int dayIndex = lessons.getInt("day_index");
			
			//Does the day already exist?
			PhTPDay dayExist = null;
			PhTPWeek byIndex = getWeekByIndex(weekTemp, weekIndex);
			if(byIndex != null) {
				for(PhTPDay day : weekTemp.get(weekIndex).days) {
					if(dayIndex == day.getDayIndex()) {
						dayExist = day;
						break;
					}
				}
			} else {
				System.err.println("Lesson referring to non existent week index " + weekIndex); //<- should not happen
			}
			
			if(dayExist == null) {
				dayExist = new PhTPDay();
				dayExist.lessons.add(lesson);
				weekTemp.get(weekIndex).days.add(dayExist);
			} else dayExist.lessons.add(lesson);
		}
		
		model.weeks = weekTemp.toArray(new PhTPWeek[weekTemp.size()]);
		return model;
	}
	
	static PhTPWeek getWeekByIndex(ArrayList<PhTPWeek> list, int index) {
		for(PhTPWeek week : list) {
			if(week.getIndex() == index)
				return week;
		}
		return null;
	}
	
	/**Parses a valid PhTP json into a model for easier usage and error checking*/
	public static PhTPModel parse(JsonObject object) throws IllegalArgumentException {
		PhTPModel model = new PhTPModel();
		
		//Parse standart fields
		JsonPrimitive version = getMandatoryField(object, "version");
		model.version = version.getAsString();
		
		JsonPrimitive classId = getMandatoryField(object, "classId");
		model.classId = classId.getAsInt();
		
		JsonPrimitive blockStart = getMandatoryField(object, "blockStart");
		model.blockStart = PhTPDate.parse(blockStart.getAsString());
		
		JsonPrimitive blockEnd = getMandatoryField(object, "blockEnd");
		model.blockEnd = PhTPDate.parse(blockEnd.getAsString());
		
		if(model.blockEnd.getDate().getTimeInMillis() < model.blockStart.getDate().getTimeInMillis())
			throw new IllegalArgumentException("Block end " + blockEnd.getAsString() + " can't start earlier than block start " + blockStart.getAsString());
		
		//Parse weeks
		JsonArray weeks = object.getAsJsonArray("weeks");
		if(weeks == null) throw new IllegalArgumentException("Mandatory value not present: 'weeks' in " + object);
		if(weeks.size() == 0) throw new IllegalArgumentException("Field 'weeks' is empty");
		
		final ArrayList<PhTPWeek> parsedWeeks = new ArrayList<>();
		int maxHours = 0;
		
		for(int i = 0; i < weeks.size(); i++) {
			JsonObject element = weeks.get(i).getAsJsonObject();
			
			PhTPWeek week = new PhTPWeek();
			
			JsonPrimitive weekStart = getMandatoryField(element, "start");
			week.start = PhTPDate.parse(weekStart.getAsString());
			if(week.start.getDate().getTimeInMillis() < model.blockStart.getDate().getTimeInMillis() || week.start.getDate().getTimeInMillis() > model.blockEnd.getDate().getTimeInMillis())
				throw new IllegalArgumentException("Week start " + weekStart.getAsString() + " for block week " + i + " lies outside of the block: " + blockStart.getAsString() + "->" + blockEnd.getAsString());
			
			JsonPrimitive weekEnd = getMandatoryField(element, "end");
			week.end = PhTPDate.parse(weekEnd.getAsString());
			if(week.end.getDate().getTimeInMillis() < model.blockStart.getDate().getTimeInMillis() || week.end.getDate().getTimeInMillis() > model.blockEnd.getDate().getTimeInMillis())
				throw new IllegalArgumentException("Week end " + weekStart.getAsString() + " for block week " + i + " lies outside of the block: " + blockStart.getAsString() + "->" + blockEnd.getAsString());
			
			//Parse days inside the week. Also later fill up empty days / hours to create a clean 2d array as a timetable
			JsonArray days = element.getAsJsonArray("days");
			if(days == null) throw new IllegalArgumentException("Mandatory value not present: 'days' in " + element);
			if(days.size() == 0) throw new IllegalArgumentException("Field 'days' for week is empty");
			
			ArrayList<PhTPDay> daysTemp = new ArrayList<>();
			for(int j = 0; j < days.size(); j++) {
				JsonArray hours = days.get(j).getAsJsonArray();
				ArrayList<PhTPLesson> lessonsTemp = new ArrayList<>();
				
				for(int k = 0; k < hours.size(); k++) {
					JsonObject lessonElement = hours.get(k).getAsJsonObject();
					PhTPLesson lesson = new PhTPLesson();
					
					if(lessonElement.getAsJsonPrimitive("index") == null)
						throw new IllegalArgumentException("Mandatory field 'index' for lesson " + lessonElement + " on day " + j + " missing");
					if(lessonElement.getAsJsonPrimitive("lessonId") == null)
						throw new IllegalArgumentException("Mandatory field 'lessonId' for lesson " + lessonElement + " on day " + j + " missing");
					
					//If other values are missing, just replace them by a 'unknown' value
					lesson.lessonId = lessonElement.getAsJsonPrimitive("lessonId").getAsInt();
					lesson.index = lessonElement.getAsJsonPrimitive("index").getAsInt();
					
					if(lesson.index > ServerMain.SERVER_CONFIG.maxLessonsPerDay) 
						throw new IllegalArgumentException("Max lessons per day exceeded or invalid lesson index: " + lesson.index + " for lesson " + lessonElement + " on day " + j + " missing");
					
					lesson.firstPhase = lessonElement.getAsJsonPrimitive("phase1") == null ? Phases.UNKNOWN : Phases.parse(lessonElement.getAsJsonPrimitive("firstPhase").getAsShort());
					lesson.secondPhase = lessonElement.getAsJsonPrimitive("phase2") == null ? Phases.UNKNOWN : Phases.parse(lessonElement.getAsJsonPrimitive("secondPhase").getAsShort());
					
					//Already sort
					boolean added = false;
					for(int l = 0; l < lessonsTemp.size() && !added; l++) {
						if(lessonsTemp.get(l).index >= lesson.index) {
							lessonsTemp.add(l, lesson);
							added = true;
						}
					}
					if(!added) lessonsTemp.add(lesson);
					
					if(lesson.index > maxHours) maxHours = lesson.index;
				}
				
				PhTPDay day = new PhTPDay();
				day.lessons = lessonsTemp;
				daysTemp.add(day);
			}
			
			week.days = daysTemp;
			parsedWeeks.add(week);
		}
		
		model.weeks = parsedWeeks.toArray(new PhTPWeek[parsedWeeks.size()]);
		return model;
	}
	
	static JsonPrimitive getMandatoryField(JsonObject object, String memberName) {
		JsonPrimitive value = object.getAsJsonPrimitive(memberName);
		if(value == null)
			throw new IllegalArgumentException("Mandatory variable '" + memberName + "' missing in " + object);
		return value;
			
	}
	
	/**Parses a given model to a json*/
	public static String parse(PhTPModel model) {
		//No further checks are nessecary here: The model had to be already parsed successfully
		
		StringBuilder json = new StringBuilder();
		json.append("{"
				+ "\"version\": 1,"
				+ "\"classId\": " + model.getClassId() + ","
				+ "\"blockStart\": " + Utils.convertToUntisDate(model.getBlockStart().getDate()) + ","
				+ "\"blockEnd\": " + Utils.convertToUntisDate(model.getBlockEnd().getDate()) + ","
				+ "\"weeks\": [");
		
		//Add weeks
		for(PhTPWeek week : model.weeks) {
			StringBuilder jsonWeek = new StringBuilder();
			jsonWeek.append("{"
					+ "\"start\": " + Utils.convertToUntisDate(week.start.getDate()) + ","
					+ "\"end\": " + Utils.convertToUntisDate(week.end.getDate()) + ","
					+ "\"days\": [");
		
			for(PhTPDay days : week.days) {
				jsonWeek.append("[");
					
				for(PhTPLesson lesson : days.lessons) {
					jsonWeek.append("{"
							+ "\"index\": " + lesson.index + ","
							+ "\"phase1\": " + lesson.getFirstPhase().getId() + ","
							+ "\"phase2\": " + lesson.getSecondPhase().getId() + ","
							+ "\"lessonId\": " + lesson.getLessonId() + "},");
				}
				
				if(days.lessons.size() > 0) jsonWeek.deleteCharAt(jsonWeek.length()-1);
				
				jsonWeek.append("],");
			}
			
			if(week.days.size() > 0) jsonWeek = jsonWeek.deleteCharAt(jsonWeek.length()-1);
			json.append(jsonWeek.toString() + "]},");
		}
		
		if(model.weeks.length > 0) json = json.deleteCharAt(json.length()-1);
		json.append("]}");
		return json.toString();
	}
}
