package ucsf.sod.objects;

import java.time.LocalTime;

import ucsf.sod.util.SODDateUtils;

public interface Period {

	public abstract LocalTime getStartTime();
	public abstract LocalTime getEndTime();
	
	public default boolean equivalent(Period p) {
		return (isAM() == p.isAM() || isPM() == p.isPM());
	}
	
	public default boolean isAM() {
		return toString().endsWith("AM");
	}
	
	public default boolean isPM() {
		return toString().endsWith("PM");
	}
	
	public default String getMeridian() {
		var meridian = this.toString();
		return meridian.substring(meridian.lastIndexOf('_')+1);
	}
	
	public default boolean inRange(LocalTime start, LocalTime end) {
		if(SODDateUtils.timeIsOnOrBefore(getStartTime(), start)) {
			if(SODDateUtils.timeIsOnOrAfter(getEndTime(), end)) {
				return true;
			} else if(SODDateUtils.timeIsOnOrBefore(getEndTime(), start)) { // screen ranges that are completely after getStart() - getEnd()
				return false;
			}
		} else {
			if(SODDateUtils.timeIsOnOrAfter(getStartTime(), end)) { // screen ranges that are completely before getStart() - getEnd()
				return false;
			} else if(SODDateUtils.timeIsOnOrBefore(getEndTime(), end)) { // start and end envelops this interval
				return true;
			}
		}
		
		throw new RuntimeException("Unable to determine if start="+start+",end="+end+" fits within [ "+getStartTime()+" - "+getEndTime()+" ]");
	}
}