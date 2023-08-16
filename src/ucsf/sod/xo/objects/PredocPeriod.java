package ucsf.sod.xo.objects;

import java.time.LocalTime;

import ucsf.sod.objects.Period;
import ucsf.sod.util.SODDateUtils;

public enum PredocPeriod implements Period {

	PREDOC_AM(LocalTime.of(8, 30), LocalTime.of(11, 30)) {
		@Override
		public boolean inRange(LocalTime start, LocalTime end) {
			if(SODDateUtils.timeIsOnOrBefore(getStartTime(), start) && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(12,0))) {
				return true;
			} else if(SODDateUtils.timeIsOnOrAfter(start, LocalTime.of(7,30)) && SODDateUtils.timeIsOnOrBefore(end, getEndTime())) {
				return true;
			} else if(SODDateUtils.timeIsOnOrAfter(start, LocalTime.of(10,30)) && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(12,10))) {
				return true;
			}
			return super.inRange(start, end);
		}
		
	},
	PREDOC_PM1(LocalTime.of(13,0), LocalTime.of(15,0)) {
		
		@Override
		public boolean isPM() {
			return true;
		}
		
		@Override
		public boolean inRange(LocalTime start, LocalTime end) {

			/**
			 * 13:15 - 15:15
			 * 13:30 - 15:30
			 * 13:30 - 17:00
			 * 14:00 - 17:00
			 */
			if(start.getHour() == 12 && SODDateUtils.timeIsOnOrBefore(end, getEndTime())) {
				return true;
			} else if(start.getHour() == 13 && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(17,0))) {
				return true;
			} else if(start.equals(LocalTime.of(14, 0)) && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(17,0))) {
				return true;
			} else if(start.equals(LocalTime.of(14, 10)) && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(15,30))) {
				return true;
			} else if(start.equals(LocalTime.of(14, 30)) && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(17,30))) {
				return true;
			} else if(start.equals(LocalTime.of(14, 45)) && end.equals(LocalTime.of(16, 45))) {
				return false;
			}

			return super.inRange(start, end);
		}
	},
	PREDOC_PM2(LocalTime.of(15,0), LocalTime.of(17,0)) {
		
		@Override
		public boolean isPM() {
			return true;
		}

		@Override
		public boolean inRange(LocalTime start, LocalTime end) {
			/**
			 * 15:30 - 17:30
			 * 15:50 - 17:20
			 * 16:15 - 17:05
			 */
			if(15 <= start.getHour() && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(17,30))) {
				return true;
			} else if(start.equals(LocalTime.of(14, 45)) && end.equals(LocalTime.of(16, 45))) {
				return true;
			}

			return super.inRange(start, end);
		}
	};
	
	public final LocalTime start;
	public final LocalTime end;
	
	PredocPeriod(LocalTime start, LocalTime end) {
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

	public boolean isPM2() {
		return toString().endsWith("PM2");
	}
	
	public static PredocPeriod getPeriod(String s) {
		s = s.toUpperCase();
		if("AM".equals(s)) {
			return PREDOC_AM;
		} else if("PM1".equals(s)) {
			return PREDOC_PM1;
		} else if("PM2".equals(s)) {
			return PREDOC_PM2;
		}
		
		throw new RuntimeException("Unrecognized predoc period: " + s);
	}
	
	public static PredocPeriod getPeriod(LocalTime start, LocalTime end) {
		for(PredocPeriod p : values()) {
			if(p.inRange(start, end))
				return p;
		}
		
		throw new RuntimeException("Unable to determine predoc period: start="+start+", end=" +end);
	}
	
	public static boolean isPredocPeriod(Period p) {
		return p.toString().startsWith("PREDOC");
	}
}
