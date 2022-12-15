package com.devkev.database;

/**Hier befinden sich alle Phasenenums, sowie mit welchen vorgegebenen Enums sie in der Datenbank gespeichert werden.
 * !!! DAS VERÄNDERN DIESER ENUMS KANN IN EINER BEREITS EXISTIERENDEN DATENBANK ZU ANOMALIEN FÜHREN !!!*/
public enum Phases {
	
	UNKNOWN((short) 0),
	ORIENTING((short) 1),
	PLANNING((short) 2),
	STRUCTURED((short) 3),
	FREE((short) 4),
	FEEDBACK((short) 5);
	
	short id;
	
	Phases(short id) {
		this.id = id;
	}
	
	public short getId() {
		return id;
	}
	
	public static Phases parse(short id) {
		for(Phases p : Phases.values()) {
			if(id == p.id) 
				return p;
		}
		return Phases.UNKNOWN;
	}
}
