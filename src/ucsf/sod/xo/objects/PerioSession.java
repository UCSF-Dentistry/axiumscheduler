package ucsf.sod.xo.objects;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import ucsf.sod.objects.GenericSession;
import ucsf.sod.objects.Period;
import ucsf.sod.objects.Session;

public enum PerioSession implements Session {
	
	PERIO_SUNDAY_AM(DayOfWeek.SUNDAY, Clinic.PERIO, PerioPeriod.PERIO_AM),
	PERIO_SUNDAY_PM(DayOfWeek.SUNDAY, Clinic.PERIO, PerioPeriod.PERIO_PM),
	PERIO_MONDAY_AM(DayOfWeek.MONDAY, Clinic.PERIO, PerioPeriod.PERIO_AM),
	PERIO_MONDAY_PM(DayOfWeek.MONDAY, Clinic.PERIO, PerioPeriod.PERIO_PM),
	PERIO_TUESDAY_AM(DayOfWeek.TUESDAY, Clinic.PERIO, PerioPeriod.PERIO_AM),
	PERIO_TUESDAY_PM(DayOfWeek.TUESDAY, Clinic.PERIO, PerioPeriod.PERIO_PM),
	PERIO_WEDNESDAY_AM(DayOfWeek.WEDNESDAY, Clinic.PERIO, PerioPeriod.PERIO_AM),
	PERIO_WEDNESDAY_PM(DayOfWeek.WEDNESDAY, Clinic.PERIO, PerioPeriod.PERIO_PM),
	PERIO_THURSDAY_AM(DayOfWeek.THURSDAY, Clinic.PERIO, PerioPeriod.PERIO_AM),
	PERIO_THURSDAY_PM(DayOfWeek.THURSDAY, Clinic.PERIO, PerioPeriod.PERIO_PM),
	PERIO_FRIDAY_AM(DayOfWeek.FRIDAY, Clinic.PERIO, PerioPeriod.PERIO_AM),
	PERIO_FRIDAY_PM(DayOfWeek.FRIDAY, Clinic.PERIO, PerioPeriod.PERIO_PM),
	PERIO_SATURDAY_AM(DayOfWeek.SATURDAY, Clinic.PERIO, PerioPeriod.PERIO_AM),
	PERIO_SATURDAY_PM(DayOfWeek.SATURDAY, Clinic.PERIO, PerioPeriod.PERIO_PM);
	
	public final DayOfWeek day;
	public final Clinic clinic;
	public final PerioPeriod period;
	
	PerioSession(DayOfWeek d, Clinic c, PerioPeriod p) {
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
	
	public static PerioSession toSession(String s) {
		Session _s = GenericSession.toSession(s);
		return PerioSession.valueOf("PERIO_" + _s.toString());
	}

	public static PerioSession toSession(LocalDate date, Period p) {
		if(!p.toString().startsWith("PERIO")) {
			p = PerioPeriod.getPeriod(p.getStartTime(), p.getEndTime());
		}
		return PerioSession.valueOf("PERIO_" + date.getDayOfWeek() + "_" + p.getMeridian());
	}

	public static PerioSession toSession(LocalDate date, LocalTime start, LocalTime end) {
		return PerioSession.valueOf("PERIO_" + date.getDayOfWeek() + "_" + PerioPeriod.getPeriod(start, end).getMeridian());
	}
}
