package ucsf.sod.xo.objects;

import java.time.LocalTime;

import ucsf.sod.objects.Period;
import ucsf.sod.util.SODDateUtils;

public enum PerioPeriod implements Period {
	
	PERIO_AM(LocalTime.of(8, 30), LocalTime.of(12, 0)) {
		@Override
		public boolean inRange(LocalTime start, LocalTime end) {
			
			if(SODDateUtils.timeIsOnOrBefore(getStartTime(), start) && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(12,30))) {
				return true;
			/**
			 *  08:00 - 12:00
			 *  08:15 - 09:15
			 */
			} else if(SODDateUtils.timeIsOnOrAfter(start, LocalTime.of(8, 0)) && SODDateUtils.timeIsOnOrBefore(end, getEndTime())) {
				return true;
			}
			
			return super.inRange(start, end);
		}
	},
	PERIO_PM(LocalTime.of(13, 30), LocalTime.of(17, 0)) {
		@Override
		public boolean inRange(LocalTime start, LocalTime end) {

			if(SODDateUtils.timeIsOnOrBefore(getStartTime(), start) && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(17,30))) {
				return true;
			} else if(SODDateUtils.timeIsOnOrAfter(start, LocalTime.of(13,0)) && SODDateUtils.timeIsOnOrBefore(end, getEndTime())) {
				return true;
			}
			
			return super.inRange(start, end);
		}
	};
	
	public final LocalTime start;
	public final LocalTime end;
	
	PerioPeriod(LocalTime start, LocalTime end) {
		this.start = start;
		this.end = end;
	}

	@Override
	public LocalTime getStartTime() {
		return start;
	}

	@Override
	public LocalTime getEndTime() {
		return end;
	}

	public static PerioPeriod getPeriod(LocalTime start, LocalTime end) {
		for(PerioPeriod p : values()) {
			if(p.inRange(start, end))
				return p;
		}
		
		throw new RuntimeException("Unable to determine perio period: start="+start+", end=" +end);
	}

	public static boolean isPerioPeriod(Period p) {
		return p.toString().startsWith("PERIO");
	}
}
