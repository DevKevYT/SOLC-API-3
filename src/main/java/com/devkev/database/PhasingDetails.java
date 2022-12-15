package com.devkev.database;

import java.sql.Date;
import java.util.Calendar;

public class PhasingDetails {
	
	int classId;
	private Calendar startDate;
	private Calendar endDate;
	private Calendar uploadedAt;
	int ownerId;
	String ownerDisplayName;
	
	void setStartDate(Date startDate) {
		this.startDate = Calendar.getInstance();
		this.startDate.setTime(startDate);
	}
	
	void setEndDate(Date endDate) {
		this.endDate = Calendar.getInstance();
		this.endDate.setTime(endDate);
	}
	
	void setUploadedAt(Date uploadedAt) {
		this.uploadedAt = Calendar.getInstance();
		this.uploadedAt.setTime(uploadedAt);
	}

	public int getClassId() {
		return classId;
	}
	
	public Calendar getStartDate() {
		return startDate;
	}
	
	public Calendar getEndDate() {
		return endDate;
	}
	
	public Calendar getUploadedAt() {
		return endDate;
	}
	
	public int getOwnderId() {
		return ownerId;
	}
	
	public String getOwnerDisplayName() {
		return ownerDisplayName;
	}
}
