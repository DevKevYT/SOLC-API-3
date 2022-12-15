package com.devkev.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devkev.phtp.Exceptions.PhasingCannotBeOverwritten;
import com.devkev.phtp.Exceptions.PhasingNotFound;
import com.devkev.phtp.PhTPModelParser;
import com.devkev.phtp.PhTPModelParser.PhTPDay;
import com.devkev.phtp.PhTPModelParser.PhTPLesson;
import com.devkev.phtp.PhTPModelParser.PhTPModel;
import com.devkev.phtp.PhTPModelParser.PhTPWeek;
import com.devkev.server.ServerConfiguration;
import com.devkev.server.ServerMain;
import com.devkev.server.Utils;

/**Diese Klasse managed alle Datenbank verbindungen, liest entsprechende Konfigurationen und handled Queries*/
public class DBConnection {
	
	private final Logger logger;
	
	private final ServerConfiguration configuration;
	
	public DBConnection(ServerConfiguration configuration) throws ClassNotFoundException, SQLException {
		logger  = LoggerFactory.getLogger(ServerMain.class);
		
		this.configuration = configuration;
		
		logger.debug("Checking jdbc Driver ...");
		Class.forName("com.mysql.cj.jdbc.Driver");
		
		Connection testConnection;
		try {
			logger.info("Checking MySQL server connection ...");
			testConnection = DriverManager.getConnection("jdbc:mysql://" + configuration.dbAddress + ":" 
						+ configuration.dbPort + "/?user=" + configuration.dbUsername + "&password=" + configuration.dbPassword);
		} catch(SQLException exception) {
			logger.error("Failed to establish a MySQL connection to " + configuration.dbAddress + " using port " + configuration.dbPort 
					+ ". Please ensure you have a MySQL server up and running on the desired address and port.\nAlso check the given credentials may cause a failing login.\n"
					+ "SQL State: " + exception.getSQLState());
			return;
		}
		
		logger.debug("Database connection established");
		
	    checkSOLCSchema(testConnection);
	   
	    testConnection.close();
	}
	
	/**Generates a schema with information from the configuration with all nessecary tables using a test connection
	 * @throws SQLException */
	private void checkSOLCSchema(Connection connection) throws SQLException {
		logger.debug("Checking schema ...");
		
		Statement createSchema = connection.createStatement();
		createSchema.executeUpdate("CREATE DATABASE IF NOT EXISTS " + configuration.dbSchemaName);
		createSchema.executeUpdate("USE " + configuration.dbSchemaName);
    	createSchema.close();
	    
	    //Create potentially missing tables
	    Statement checkPhasingTable = connection.createStatement();
	    checkPhasingTable.executeUpdate("CREATE TABLE IF NOT EXISTS phasing ("
			+ "class_id INT NOT NULL,"
			+ "start_date DATE NOT NULL,"
			+ "end_date DATE NOT NULL,"
			+ "uploaded_at DATETIME,"
			+ "owner_id INT,"
			+ "owner_displayName varchar(255),"
			+ "PRIMARY KEY (class_id))");
	    checkPhasingTable.close();
	    
	    Statement checkLessonTable = connection.createStatement();
	    checkLessonTable.executeUpdate("CREATE TABLE IF NOT EXISTS lesson ("
	    		+ "class_id INT NOT NULL,"
	    		+ "lesson_id INT NOT NULL,"
	    		+ "week_index INT,"
	    		+ "first_phase TINYINT NOT NULL,"
	    		+ "second_phase TINYINT NOT NULL,"
	    		+ "day_index INT,"
	    		+ "hour_index INT,"
	    		+ "FOREIGN KEY (class_id) REFERENCES phasing(class_id))");
	    checkLessonTable.close();
	    
	    Statement checkWeeksTable = connection.createStatement();
	    checkWeeksTable.executeUpdate("CREATE TABLE IF NOT EXISTS week_definition ("
	    		+ "class_id INT NOT NULL,"
	    		+ "week_index INT NOT NULL,"
	    		+ "start_date DATE NOT NULL,"
	    		+ "end_date DATE NOT NULL,"
	    		+ "FOREIGN KEY (class_id) REFERENCES phasing(class_id))");
	    checkWeeksTable.close();
	    
	    logger.debug("Check done");
	}
	
	private Connection createConnection() throws SQLException {
		//logger.debug("Connecting to " + "jdbc:mysql://" + configuration.dbAddress + ":" + configuration.dbPort);
		
		return DriverManager.getConnection("jdbc:mysql://" + configuration.dbAddress + ":" 
				+ configuration.dbPort + "/" + configuration.dbSchemaName + "?user=" + configuration.dbUsername + "&password=" + configuration.dbPassword);
	}
	
	public void queryUpdate(String query) throws SQLException {
		Connection c = createConnection();
		Statement stmt = c.createStatement();
		logger.debug("Executing update query: " + query);
		
	    stmt.executeUpdate(query);
	}
		
	public ResultSet query(String query) throws SQLException {
		Connection c = createConnection();
		Statement stmt = c.createStatement();
		logger.debug("Executing query: " + query);
		
	    return stmt.executeQuery(query);
	}
	
	public PhTPModel readModel(int classId) throws SQLException, PhasingNotFound {
		return PhTPModelParser.parseFromDatabase(this, classId);
	}
	
	public void deletePhasing(int classId) throws SQLException {
		queryUpdate("DELETE FROM week_definition WHERE class_id = " + classId);
		queryUpdate("DELETE FROM lesson WHERE class_id = " + classId);
		queryUpdate("DELETE FROM phasing WHERE class_id = " + classId);
	}
	
	/**Inserts a parsed phasing model. Overwrites existing ones, if requested. Otherwise an error message is thrown
	 * ClassID inside the model is used as the primary key
	 * @throws PhasingCannotBeOverwritten */
	public void insertPhaseModel(PhTPModel model, Calendar uploadedAt, int owner, String ownerDisplayName, boolean overwrite) throws SQLException, PhasingCannotBeOverwritten {
		ResultSet set = query("SELECT count(*) FROM phasing WHERE class_id = " + model.getClassId());
		set.next();
		
		if(set.getInt(1) > 0 && !overwrite) 
			throw new PhasingCannotBeOverwritten("Cannot overwrite phasing if 'overwrite' is set to false");
		else if(set.getInt(1) > 0) {
			//Es sollte alles erstmal gelöscht werden, da Stunden in der 'lesson' Tabelle sich ändern könnten
			deletePhasing(model.getClassId());
		}
		
		//TODO ensure, if one query fails, the others are deleted
		
		logger.debug("Inserting phasing for class " + model.getClassId());
		queryUpdate("INSERT INTO phasing VALUES ("
				+ model.getClassId() + ","
				+ "'" + Utils.toSQLDate(model.getBlockStart().getDate()) + "',"
				+ "'" + Utils.toSQLDate(model.getBlockEnd().getDate()) + "',"
				+ "'" + Utils.toSQLDateTime(uploadedAt) + "',"
				+ "'" + owner + "',"
				+ "'" + ownerDisplayName + "')");
		
		int week = 0;
		for(PhTPWeek weeks : model.getWeeks()) {
			int dayIndex = 0;
			for(PhTPDay days : weeks.getDays()) {
				for(PhTPLesson lesson : days.getLessons()) {
					
					logger.debug("Inserting lesson id " + lesson.getLessonId() + " for class " + model.getClassId() + " in week " + week + " at day " + dayIndex + " in hour " + lesson.getIndex());
					queryUpdate("INSERT INTO lesson VALUES ("
							+ model.getClassId() + ","
							+ lesson.getLessonId() + ","
							+ week + ","
							+ lesson.getFirstPhase().id + ","
							+ lesson.getSecondPhase().id + ","
							+ dayIndex + ","
							+ lesson.getIndex() + ")");
				}
				dayIndex++;
			}
			
			logger.debug("Adding week definition for week " + week);
			queryUpdate("INSERT INTO week_definition VALUES ("
					+ model.getClassId() + ","
					+ week + ","
					+ "'" + Utils.toSQLDate(weeks.getWeekStart().getDate()) + "',"
					+ "'" + Utils.toSQLDate(weeks.getWeekEnd().getDate()) + "')");
			
			week++;
		}
	}
	
	public PhasingDetails[] getPhasingDetails(Integer ... classId) throws SQLException {
		ArrayList<PhasingDetails> temp = new ArrayList<>();
		
		if(classId.length > 0) {
			String array = "(";
			for(int i = 0; i < classId.length; i++) 
				array += classId[i] + (i < classId.length-1 ? "," : "");
			
			ResultSet set = query("SELECT * FROM phasing WHERE class_id in " + array + ")");
			while(set.next()) {
				logger.debug("Phasing details found for class " + set.getInt("class_id"));
				
				PhasingDetails d = new PhasingDetails();
				d.classId = set.getInt("class_id");
				d.setStartDate(set.getDate("start_date"));
				d.setEndDate(set.getDate("end_date"));
				d.setUploadedAt(set.getDate("uploaded_at"));
				d.ownerId = set.getInt("owner_id");
				d.ownerDisplayName = set.getString("owner_displayName");
				temp.add(d);
			}
		}
		return temp.toArray(new PhasingDetails[temp.size()]);
	}
}
