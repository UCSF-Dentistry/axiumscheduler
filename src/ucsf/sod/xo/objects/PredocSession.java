package ucsf.sod.xo.objects;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import ucsf.sod.objects.Period;
import ucsf.sod.objects.Session;

public enum PredocSession implements Session {
	PREDOC_MONDAY_AM(DayOfWeek.MONDAY, Clinic.PREDOC, PredocPeriod.PREDOC_AM),
	PREDOC_MONDAY_PM1(DayOfWeek.MONDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM1) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	},
	PREDOC_MONDAY_PM2(DayOfWeek.MONDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM2) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	},
	PREDOC_TUESDAY_AM(DayOfWeek.TUESDAY, Clinic.PREDOC, PredocPeriod.PREDOC_AM),
	PREDOC_TUESDAY_PM1(DayOfWeek.TUESDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM1) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	},
	PREDOC_TUESDAY_PM2(DayOfWeek.TUESDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM2) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	},
	PREDOC_WEDNESDAY_AM(DayOfWeek.WEDNESDAY, Clinic.PREDOC, PredocPeriod.PREDOC_AM),
	PREDOC_WEDNESDAY_PM1(DayOfWeek.WEDNESDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM1) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	},
	PREDOC_WEDNESDAY_PM2(DayOfWeek.WEDNESDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM2) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	},
	PREDOC_THURSDAY_AM(DayOfWeek.THURSDAY, Clinic.PREDOC, PredocPeriod.PREDOC_AM),
	PREDOC_THURSDAY_PM1(DayOfWeek.THURSDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM1) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	},
	PREDOC_THURSDAY_PM2(DayOfWeek.THURSDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM2) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	},
	PREDOC_FRIDAY_AM(DayOfWeek.FRIDAY, Clinic.PREDOC, PredocPeriod.PREDOC_AM),
	PREDOC_FRIDAY_PM1(DayOfWeek.FRIDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM1) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	},
	PREDOC_FRIDAY_PM2(DayOfWeek.FRIDAY, Clinic.PREDOC, PredocPeriod.PREDOC_PM2) {
		@Override
		public boolean isPM() {
			return this.toString().contains("PM");
		}
	};

	public final DayOfWeek day;
	public final Clinic clinic;
	public final Period period;
	
	PredocSession(DayOfWeek d, Clinic c, Period p) {
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
	
	public boolean isPM1() {
		return toString().endsWith("PM1");
	}
	
	public boolean isPM2() {
		return toString().endsWith("PM2");
	}

	public static PredocSession toSession(LocalDate d, Period p) {
		return toSession(d, p.getStartTime(), p.getEndTime());
	}
	
	public static PredocSession toSession(LocalDate date, LocalTime start, LocalTime end) {
		return PredocSession.valueOf("PREDOC_" + date.getDayOfWeek() + "_" + PredocPeriod.getPeriod(start, end).getMeridian());
	}
	
	public static boolean isPredocSession(Session s) {
		return s.toString().startsWith("PREDOC");
	}
}
