package ucsf.sod.xo.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ucsf.sod.objects.StudentYear;
import ucsf.sod.util.SODDateUtils;
import ucsf.sod.xo.XOGridUtils.ClinicStatus;
import ucsf.sod.xo.XOGridUtils.ClinicStatus.OpenMode;
import ucsf.sod.xo.objects.Student;

public enum AcademicCalendar {
	AY1998_1999(1998),
	AY1999_2000(1999),
	AY2000_2001(2000),
	AY2001_2002(2001),
	AY2002_2003(2002),
	AY2003_2004(2003),
	AY2004_2005(2004),
	AY2005_2006(2005),
	AY2006_2007(2006),
	AY2007_2008(2007),
	AY2008_2009(2008),
	AY2009_2010(2009),
	AY2010_2011(2010),
	AY2011_2012(2011),
	AY2012_2013(2012),
	AY2013_2014(2013),
	AY2014_2015(2014),
	AY2015_2016(2015),
	AY2016_2017(2016),
	AY2017_2018(2017),
	AY2018_2019(2018),
	AY2019_2020(2019),
	AY2020_2021(2020) {
		@Override
		protected Map<LocalDate, ClinicStatus> buildAcademicCalendar() {
			
			Map<LocalDate, ClinicStatus> calendar = super.buildAcademicCalendar();

			LocalDate _t;
			_t = LocalDate.of(startYear, Month.JULY, 6);
			calendar.put(_t, new ClinicStatus(_t, "Orientation", OpenMode.CLOSED));
			calendar.put(_t = LocalDate.of(startYear, Month.JULY, 7), new ClinicStatus(_t, "Orientation", OpenMode.CLOSED));
			
			// Election Day
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(startYear, Month.NOVEMBER, 1), DayOfWeek.TUESDAY);
			calendar.put(_t, new ClinicStatus(_t, "Election Day", OpenMode.OPEN));

			// Winter Break
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(startYear, Month.DECEMBER, 1), DayOfWeek.FRIDAY).plusWeeks(2); // Find last day of clinic for the calendar year
			_t = SODDateUtils.ceilingToNextMonday(_t);
			calendar.putIfAbsent(_t, new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			_t = SODDateUtils.ceilingToNextMonday(_t);
			while(_t.getYear() == startYear) {
				calendar.putIfAbsent(_t, new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
				_t = _t.plusDays(1);
			}

			// ADEXs
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(endYear, Month.APRIL, 1), DayOfWeek.FRIDAY).plusWeeks(2);
			calendar.put(_t, new ClinicStatus(_t, "WREB", OpenMode.AM_ONLY));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "ADEXs", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "ADEXs", OpenMode.CLOSED));

			// WREBs
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(endYear, Month.MAY, 1), DayOfWeek.FRIDAY).plusWeeks(1);
			calendar.put(_t, new ClinicStatus(_t, "WREB", OpenMode.AM_ONLY));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "WREB", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "WREB", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "WREB", OpenMode.PM_ONLY));
			
			// Summer Break
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(endYear, Month.JUNE, 1), DayOfWeek.FRIDAY).plusWeeks(1); // Find last day for D4s
			_t = SODDateUtils.ceilingToNextMonday(_t).plusWeeks(1);
			calendar.put(_t, new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			
			return calendar;
		}
		
		@Override
		public LocalDate getAcademicStartDate() {
			return LocalDate.of(startYear, ACADEMIC_YEAR.get(0), 6);
		}
	},
	AY2021_2022(2021) {
		@Override
		public LocalDate getAcademicStartDate() {
			return LocalDate.of(startYear, Month.JUNE, 14);
		}
	},
	AY2022_2023(2022) {
		@Override
		protected Map<LocalDate, ClinicStatus> buildAcademicCalendar() {
			
			Map<LocalDate, ClinicStatus> calendar = super.buildAcademicCalendar();

			LocalDate _t;
			// Start Summer Break
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(startYear, Month.JUNE, 1), DayOfWeek.FRIDAY).plusWeeks(1); // Find last day for D4s from previous academic year
			_t = SODDateUtils.ceilingToNextMonday(_t).plusWeeks(1);
			calendar.put(_t, new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			
			// Winter Break
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(startYear, Month.DECEMBER, 1), DayOfWeek.FRIDAY).plusWeeks(2); // Find last day of clinic for the calendar year
			_t = SODDateUtils.ceilingToNextMonday(_t);
			calendar.putIfAbsent(_t, new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			_t = SODDateUtils.ceilingToNextMonday(_t);
			while(_t.getYear() == startYear) {
				calendar.putIfAbsent(_t, new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
				_t = _t.plusDays(1);
			}

			// End Summer Break
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(endYear, Month.JUNE, 1), DayOfWeek.FRIDAY).plusWeeks(1); // Find last day for D4s
			_t = SODDateUtils.ceilingToNextMonday(_t).plusWeeks(1);
			calendar.put(_t, new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			
			return calendar;
		}
	},
	AY2023_2024(2023) {
		@Override
		protected Map<LocalDate, ClinicStatus> buildAcademicCalendar() {
			
			Map<LocalDate, ClinicStatus> calendar = super.buildAcademicCalendar();
			
			LocalDate _t;
			// Start Summer Break
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(startYear, Month.JUNE, 1), DayOfWeek.FRIDAY).plusWeeks(1); // Find last day for D4s from previous academic year
			_t = SODDateUtils.ceilingToNextMonday(_t).plusWeeks(_t.plusYears(1).isLeapYear() ? 2 : 1);
			calendar.put(_t, new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			
			// Winter Break
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(startYear, Month.DECEMBER, 1), DayOfWeek.FRIDAY).plusWeeks(2); // Find last day of clinic for the calendar year
			_t = SODDateUtils.ceilingToNextMonday(_t);
			calendar.putIfAbsent(_t, new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			calendar.putIfAbsent(_t = _t.plusDays(1), new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
			_t = SODDateUtils.ceilingToNextMonday(_t);
			while(_t.getYear() == startYear) {
				calendar.putIfAbsent(_t, new ClinicStatus(_t, "Winter Break", OpenMode.CLOSED));
				_t = _t.plusDays(1);
			}

			// End Summer Break
			_t = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(endYear, Month.JUNE, 1), DayOfWeek.FRIDAY).plusWeeks(1); // Find last day for D4s
			_t = SODDateUtils.ceilingToNextMonday(_t).plusWeeks(1);
			calendar.put(_t, new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			calendar.put(_t = _t.plusDays(1), new ClinicStatus(_t, "Summer Break", OpenMode.CLOSED));
			
			return calendar;
		}
		
		@Override
		public Quarter toQuarter(LocalDate date) {
			int year = date.getYear();
			if(year == startYear) {
				if(date.isBefore(LocalDate.of(startYear, Month.SEPTEMBER, 18))) {
					return SODDateUtils.dateIsOnOrAfter(date, LocalDate.of(startYear, Month.JUNE, 12)) ? Quarter.SUMMER : null;
				} else {
					return Quarter.FALL;
				}
			} else if(year == endYear) {
				if(date.isBefore(LocalDate.of(endYear, Month.MARCH, 25))) {
					return Quarter.WINTER;
				} else {
					return SODDateUtils.dateIsOnOrBefore(date, LocalDate.of(endYear, Month.JUNE, 14)) ? Quarter.SPRING : null;
				}
			} else {
				return null;
			}
		}
	},
	AY2024_2025(2024),
	AY2025_2026(2025)
	;

	public static AcademicCalendar CURRENT_YEAR = getCalendar();

	private final Map<LocalDate, ClinicStatus> calendar;
	public final int startYear;
	public final int endYear;
	
	private AcademicCalendar(int year) {
		this.startYear = year;
		this.endYear = year+1;
		this.calendar = buildAcademicCalendar();
	}
	
	private AcademicCalendar(LocalDate start, LocalDate end) {
		this.startYear = start.getYear();
		this.endYear = end.getYear();
		if(endYear - startYear != 1) {
			throw new IllegalArgumentException("Start year ["+startYear+"] and end year ["+endYear+"] are different.");
		}
		this.calendar = buildAcademicCalendar();
	}
	
	/**
	 * A notable date is generally a holiday
	 * @param date
	 * @return
	 */
	public boolean isNotableDate(LocalDate date) {
		return calendar.containsKey(date);
	}
	
	public ClinicStatus get(LocalDate date) {
		switch(date.getDayOfWeek()) {
		case SATURDAY:
		case SUNDAY:
			return ClinicStatus.CLOSED;
		default:
			return calendar.getOrDefault(date, ClinicStatus.TYPICAL);
		}
	}
	
	public static LocalDate getClinicStartDate(Student s) {
		AcademicCalendar calendar = AcademicCalendar.getCalendar(s.getGraduationYear()-2); // calendars are based on July of the given year
		switch(s.year) {
		case FOURTH_YEAR:
			return calendar.getPreviousCalendar().getAcademicStartDate();
		case THIRD_YEAR:
			return calendar.getAcademicStartDate();
		case SECOND_YEAR:
			return calendar.getNextCalendar().getAcademicStartDate();
		default:
			return null;
		}
	}
	
	public LocalDate getAcademicStartDate() {
		return LocalDate.of(startYear, ACADEMIC_YEAR.get(0), 1);
	}
	
	public LocalDate getAcademicEndDate() {
		return LocalDate.of(endYear, ACADEMIC_YEAR.get(11), 30);
	}

	/**
	 * Validates if date falls in the start and end dates of this Academic Calendar
	 * @param date
	 * @return true, if the date falls between start and end dates (inclusive); false, otherwise.
	 */
	public boolean isDateOfCalendar(LocalDate date) {
		return SODDateUtils.dateIsOnOrAfter(date, getAcademicStartDate()) && SODDateUtils.dateIsOnOrBefore(date, getAcademicEndDate());
	}
	
	public static enum Quarter {
		SUMMER,
		FALL,
		WINTER,
		SPRING;
	}
	
	public static final List<Month> ACADEMIC_YEAR = Arrays.asList(Month.JULY, Month.AUGUST, Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH, Month.APRIL, Month.MAY, Month.JUNE);

	private static Map<Integer, AcademicCalendar> calendars = null;

	public static AcademicCalendar getCalendar() {
		return getCalendar(LocalDate.now());
	}
	
	public static AcademicCalendar getCalendar(LocalDate date) {
		return getCalendar(date.getYear() + (date.getMonthValue() < 7 ? -1 : 0));
	}

	public static AcademicCalendar getCalendar(int year) {
	
		if(calendars == null) {
			calendars = new TreeMap<Integer, AcademicCalendar>();
			return getCalendar(year);
		} else if(calendars.isEmpty()) {
			Map<Integer, AcademicCalendar> _calendars = new TreeMap<Integer, AcademicCalendar>();
			for(AcademicCalendar c : AcademicCalendar.values()) {
				_calendars.put(c.startYear, c);
			}
			calendars = _calendars;
		}
		
		AcademicCalendar calendar = calendars.get(year);
		if(calendar == null) {
			throw new RuntimeException("Calendar not implemented for year " + year);
		}
		return calendar;
	}
	
	public AcademicCalendar getPreviousCalendar() {
		return AcademicCalendar.getCalendar(startYear-1);
	}
	
	public AcademicCalendar getNextCalendar() {
		return AcademicCalendar.getCalendar(startYear+1);
	}
	
	static Map<LocalDate, ClinicStatus> getUniversityHolidayCalendar(int year) {
		
		Map<LocalDate, ClinicStatus> holiday = new TreeMap<LocalDate, ClinicStatus>();
		
		// New Year's Day
		var date = LocalDate.of(year, Month.JANUARY, 1);
		holiday.put(date, new ClinicStatus(date, "New Year's Day", OpenMode.CLOSED));
		
		if(date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			holiday.put(date.plusDays(1), new ClinicStatus(date, "New Year's Day", OpenMode.CLOSED));
		}
		
		// MLK Day
		date = SODDateUtils.ceilingToNextMonday(date).plusWeeks(2);
		holiday.put(date, new ClinicStatus(date, "MLK Day", OpenMode.CLOSED));
		
		// President's Day
		date = SODDateUtils.ceilingToNextMonday(LocalDate.of(year, Month.FEBRUARY, 1)).plusWeeks(2);
		holiday.put(date, new ClinicStatus(date, "President's Day", OpenMode.CLOSED));
		
		// Caesar Chavez Day
		date = SODDateUtils.floorToDayOfWeek(LocalDate.of(year, Month.MARCH, 31), DayOfWeek.FRIDAY);
		holiday.put(date, new ClinicStatus(date, "Caesar Chavez Day", OpenMode.CLOSED));
		
		// Memorial Day
		date = SODDateUtils.floorToDayOfWeek(LocalDate.of(year, Month.MAY, 31), DayOfWeek.MONDAY);
		holiday.put(date, new ClinicStatus(date, "Memorial Day", OpenMode.CLOSED));

		// Juneteenth
		date = SODDateUtils.ceilingToNextMonday(LocalDate.of(year, Month.JUNE, 1)).plusWeeks(2);
		holiday.put(date, new ClinicStatus(date, "Juneteenth", OpenMode.CLOSED));

		// Independence Day
		date = LocalDate.of(year, Month.JULY, 4);
		holiday.put(date, new ClinicStatus(date, "Independence Day", OpenMode.CLOSED));
		if(date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			holiday.put(date = date.plusDays(1), new ClinicStatus(date, "Independence Day", OpenMode.CLOSED));
		} else if(date.getDayOfWeek() == DayOfWeek.SATURDAY) {
			holiday.put(date = date.minusDays(1), new ClinicStatus(date, "Independence Day", OpenMode.CLOSED));
		}
		
		// Labor Day
		date = SODDateUtils.ceilingToNextMonday(LocalDate.of(year, Month.SEPTEMBER, 1));		
		holiday.put(date, new ClinicStatus(date, "Labor Day", OpenMode.CLOSED));
		
		// Election Day
		date = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(year, Month.NOVEMBER, 1), DayOfWeek.TUESDAY);
		holiday.put(date, new ClinicStatus(date, "Election Day", OpenMode.OPEN));

		// Veteran's Day
		date = LocalDate.of(year, Month.NOVEMBER, 11);
		holiday.put(date, new ClinicStatus(date, "Veteran's Day", OpenMode.CLOSED));
		if(date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			holiday.put(date = date.plusDays(1), new ClinicStatus(date, "Veteran's Day", OpenMode.CLOSED));
		} else if(date.getDayOfWeek() == DayOfWeek.SATURDAY) {
			holiday.put(date = date.minusDays(1), new ClinicStatus(date, "Veteran's Day", OpenMode.CLOSED));
		}

		// Thanksgiving
		date = SODDateUtils.ceilingToDayOfWeek(LocalDate.of(year, Month.NOVEMBER, 1), DayOfWeek.THURSDAY).plusWeeks(3);
		holiday.put(date, new ClinicStatus(date, "Thanksgiving", OpenMode.CLOSED));
		holiday.put(date = date.plusDays(1), new ClinicStatus(date, "Thanksgiving", OpenMode.CLOSED));
		
		// Christmas
		date = LocalDate.of(year, Month.DECEMBER, 25);
		holiday.put(date, new ClinicStatus(date, "Christmas", OpenMode.CLOSED));
		if(date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			holiday.put(date = date.plusDays(1), new ClinicStatus(date, "Christmas", OpenMode.CLOSED));
		} else {
			holiday.put(date = date.minusDays(1), new ClinicStatus(date, "Christmas", OpenMode.CLOSED));
		}

		// New Year's Eve
		date = LocalDate.of(year, Month.DECEMBER, 31);
		if(date.getDayOfWeek() != DayOfWeek.SATURDAY) {
			holiday.put(date, new ClinicStatus(date, "New Year's Eve", OpenMode.CLOSED));
		}
		
		return holiday;
	}
	
	/**
	 * Builds an academic calendar
	 * @param year the year for July 1 of academic start
	 */
	protected Map<LocalDate, ClinicStatus> buildAcademicCalendar() {

		// Gather all the holidays for the current year, and the next year
		Map<LocalDate, ClinicStatus> calendar = getUniversityHolidayCalendar(startYear);
		calendar.putAll(getUniversityHolidayCalendar(endYear));
		return calendar;
	}
	
	public StudentYear toStudentYear(Student s) {		
		if(s == null) {
			throw new IllegalArgumentException("Student is null");
		}
		
		return toStudentYear(s.id);
	}
	
	public StudentYear toStudentYear(String id) {
		
		if("0000".equals(id) || "D099".equals(id) || "D106".equals(id)) {
			return StudentYear.UNKNOWN;
		} else if(!Student.STUDENT_ID_PATTERN.matcher(id).matches()) {
			throw new IllegalArgumentException("ID of not the right format: " + id);
		}
		
		int year = 2000 + Integer.parseInt(id.substring(1,3));
		switch(year - endYear) {
			case 3: return StudentYear.FIRST_YEAR;
			case 2: return StudentYear.SECOND_YEAR;
			case 1: return StudentYear.THIRD_YEAR;
			case 0: return StudentYear.FOURTH_YEAR;
			case -1: return StudentYear.FIFTH_YEAR;
			default:
				if(year - endYear < -1)
					return StudentYear.GRADUATE;
				else
					return StudentYear.UNKNOWN;
		}
	}
	
	public Quarter toQuarter(LocalDate date) {
		throw new RuntimeException("Method not implemented");
	}	
}
