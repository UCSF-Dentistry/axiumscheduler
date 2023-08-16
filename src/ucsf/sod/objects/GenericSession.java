package ucsf.sod.objects;

import java.time.DayOfWeek;
import java.time.LocalDate;

public enum GenericSession implements Session {
	
	SUNDAY_AM(DayOfWeek.SUNDAY, Clinic.GENERIC, GenericPeriod.GENERIC_AM),
	SUNDAY_PM(DayOfWeek.SUNDAY, Clinic.GENERIC, GenericPeriod.GENERIC_PM),
	MONDAY_AM(DayOfWeek.MONDAY, Clinic.GENERIC, GenericPeriod.GENERIC_AM),
	MONDAY_PM(DayOfWeek.MONDAY, Clinic.GENERIC, GenericPeriod.GENERIC_PM),
	TUESDAY_AM(DayOfWeek.TUESDAY, Clinic.GENERIC, GenericPeriod.GENERIC_AM),
	TUESDAY_PM(DayOfWeek.TUESDAY, Clinic.GENERIC, GenericPeriod.GENERIC_PM),
	WEDNESDAY_AM(DayOfWeek.WEDNESDAY, Clinic.GENERIC, GenericPeriod.GENERIC_AM),
	WEDNESDAY_PM(DayOfWeek.WEDNESDAY, Clinic.GENERIC, GenericPeriod.GENERIC_PM),
	THURSDAY_AM(DayOfWeek.THURSDAY, Clinic.GENERIC, GenericPeriod.GENERIC_AM),
	THURSDAY_PM(DayOfWeek.THURSDAY, Clinic.GENERIC, GenericPeriod.GENERIC_PM),
	FRIDAY_AM(DayOfWeek.FRIDAY, Clinic.GENERIC, GenericPeriod.GENERIC_AM),
	FRIDAY_PM(DayOfWeek.FRIDAY, Clinic.GENERIC, GenericPeriod.GENERIC_PM),
	SATURDAY_AM(DayOfWeek.SATURDAY, Clinic.GENERIC, GenericPeriod.GENERIC_AM),
	SATURDAY_PM(DayOfWeek.SATURDAY, Clinic.GENERIC, GenericPeriod.GENERIC_PM);
	
	public final DayOfWeek day;
	public final Clinic clinic;
	public final GenericPeriod period;
	
	GenericSession(DayOfWeek d, Clinic c, GenericPeriod p) {
		this.day = d;
		this.clinic = c;
		this.period = p;
		
		if(!toString().contains(period.getMeridian())) {
			throw new IllegalArgumentException("Meridian of period ("+period.getMeridian()+") does not match Session name ("+toString()+")");
		}
	}

	@Override
	public DayOfWeek getDayOfWeek() {
		return day;
	}

	@Override
	public Clinic getClinic() {
		return clinic;
	}

	@Override
	public Period getPeriod() {
		return period;
	}
	
	public String toStringPretty() {
		return day.name().substring(0, 3) + " " + period.getMeridian();
	}

	public static GenericSession toSession(String s) {
		String[] _s = s.split("\\s+");
		if(_s.length != 2) {
			throw new IllegalArgumentException("Something malformed in session name: " + s);
		}
		
		for(GenericSession i : values()) {
			if(i.getAbbreviation().equals(_s[0])) {
				if("AM".equals(_s[1]) && i.toString().endsWith("AM")) {
					return i;
				}
				
				if("PM".equals(_s[1]) && i.toString().endsWith("PM")) {
					return i;
				}
			}
		}

		switch(_s[0]) {
		case "Thurs":
		case "Thu":
			return toSession("Thur " + _s[1]);
		case "Tue":
			return toSession("Tues " + _s[1]);
		}
		
		System.err.println("Unable to find session: " + s);
		return null;
	}
	
	public static GenericSession toSession(LocalDate d, Period p) {
		return toSession(d.getDayOfWeek(), p);
	}
	
	public static GenericSession toSession(DayOfWeek dayOfWeek, Period p) {
		var meridian = p.getMeridian();
		if(meridian.length() == 3) {
			meridian = meridian.substring(0, 2);
		}
		
		return GenericSession.valueOf(dayOfWeek + "_" + meridian);		
	}
}
