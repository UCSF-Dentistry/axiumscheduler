package ucsf.sod.objects;

import java.time.DayOfWeek;
import java.time.LocalDate;

public interface Session {

	public DayOfWeek getDayOfWeek();
	public Clinic getClinic();
	public Period getPeriod();

	public default String getAbbreviation() {
		DayOfWeek day = getDayOfWeek();
		switch(day) {
		case MONDAY: 	return "Mon";
		case TUESDAY:	return "Tues";
		case WEDNESDAY:	return "Wed";
		case THURSDAY:	return "Thur";
		case FRIDAY:	return "Fri";
		case SATURDAY:	return "Sat";
		case SUNDAY:	return "Sun";
		default:
			throw new RuntimeException("Unknown day: " + day);
		}
	}
	
	public default boolean isAM() {
		return getPeriod().isAM();
	}
	
	public default boolean isPM() {
		return getPeriod().isPM();
	}
	
	public default boolean equivalent(Session s) {
		return getAbbreviation().equals(s.getAbbreviation()) && getPeriod().equivalent(s.getPeriod()); 
	}
	
	public default DatedSession toDatedSession(LocalDate date) {
		return DatedSession.of(date, getPeriod());
	}
	
	public default String getMeridian() {
		return getPeriod().getMeridian();
	}
	
	public static enum Clinic {
		GENERIC,
		PREDOC,
		PERIO;
	}
}