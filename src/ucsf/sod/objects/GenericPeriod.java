package ucsf.sod.objects;

import java.time.LocalTime;

import ucsf.sod.util.SODDateUtils;

public enum GenericPeriod implements Period {

	GENERIC_AM(LocalTime.of(8, 0), LocalTime.of(12, 0)) {
		@Override
		public boolean inRange(LocalTime start, LocalTime end) {
			if(SODDateUtils.timeIsOnOrAfter(start, LocalTime.of(11,15)) && SODDateUtils.timeIsOnOrBefore(end, LocalTime.of(13,45))) {
				return true;
			}
			return super.inRange(start, end);
		}
	},
	GENERIC_PM(LocalTime.of(13, 0), LocalTime.of(17, 0));
	
	public final LocalTime start;
	public final LocalTime end;
	
	GenericPeriod(LocalTime start, LocalTime end) {
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
	
	public static GenericPeriod toPeriod(Period p) {
		if(p.toString().startsWith("GENERIC")) {
			return p == GENERIC_AM ? GENERIC_AM : GENERIC_PM;
		} else {
			return toPeriod(p.getStartTime(), p.getEndTime());
		}
	}
	
	public static GenericPeriod toPeriod(LocalTime start, LocalTime end) {
		
		if(start.getHour() == 2 && end.getHour() == 3) {
			return GENERIC_AM;
		}
		
		for(GenericPeriod _p : values()) {
			try {
				if(_p.inRange(start, end))
					return _p;
			} catch (RuntimeException ex) {
				if(start.getHour() < 12) {
					if(end.getHour() < 13) {
						return GENERIC_AM;
					} 
				} else if(end.getHour() <= 17) {
					return GENERIC_PM;
				}
			}
		}

		throw new RuntimeException("Unknown period: start="+start+", end="+end);
	}
}
