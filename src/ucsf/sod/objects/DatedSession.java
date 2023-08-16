package ucsf.sod.objects;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

public class DatedSession implements Session, Comparable<DatedSession> {

	public final LocalDate date;
	public final Session session;
	private DatedSession(LocalDate date, Session s) {
		
		if(s instanceof DatedSession)
			throw new IllegalArgumentException("Session is an instance of DatedSession");
		
		this.date = date;
		this.session = s;
		if(date.getDayOfWeek() != s.getDayOfWeek()) {
			throw new IllegalArgumentException("Date ["+date.getDayOfWeek()+"] and session ["+s+"] do not match");
		}
	}
	
	@Override
	public DayOfWeek getDayOfWeek() {
		return date.getDayOfWeek();
	}

	@Override
	public Clinic getClinic() {
		return session.getClinic();
	}

	@Override
	public Period getPeriod() {
		return session.getPeriod();
	}
	
	public boolean isAM() {
		return session.getPeriod().isAM();
	}
	
	public boolean isPM() {
		return session.getPeriod().isPM();
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(date, session);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		else if(obj == null || !(obj instanceof DatedSession))
			return false;
		
		DatedSession o = (DatedSession)obj;
		return date.equals(o.date) && session.equivalent(o.session);
	}
	
	@Override
	public String toString() {
		return session.toString() + "["+date+"]";
	}

	public String toStringPretty() {
		return date + " " + session.getMeridian();
	}

	@Override
	public DatedSession toDatedSession(LocalDate date) {
		return DatedSession.of(date, getPeriod());
	}

	public static DatedSession of(LocalDate date, Period p) {
		return new DatedSession(date, GenericSession.toSession(date, p));
	}
	
	@Override
	public int compareTo(DatedSession o) {
		int result = date.compareTo(o.date);
		if(result == 0) {
			result = session.getPeriod().getStartTime().compareTo(o.session.getPeriod().getStartTime());
		}
		return result;
	}
}
