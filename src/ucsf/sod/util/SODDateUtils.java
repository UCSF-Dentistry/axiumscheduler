package ucsf.sod.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.stream.Stream;

public class SODDateUtils {

	public static LocalDate floorToLastMonday(LocalDate date) {
		return floorToDayOfWeek(date, DayOfWeek.MONDAY);
	}

	public static LocalDate floorToDayOfWeek(LocalDate date, DayOfWeek dayOfWeek) {
		int differential = date.getDayOfWeek().getValue() - dayOfWeek.getValue();
		if(differential < 0) {
			differential += 7;
		}		
		return date.minusDays(differential);
	}

	public static LocalDate ceilingToDayOfWeek(LocalDate date, DayOfWeek dayOfWeek) {
		int differential = dayOfWeek.getValue() - date.getDayOfWeek().getValue();
		if(differential < 0) {
			differential += 7;
		}		
		return date.plusDays(differential);
	}

	public static LocalDate ceilingToNextMonday(LocalDate date) {
		return ceilingToDayOfWeek(date, DayOfWeek.MONDAY);
	}

	public static boolean dateTimeIsOnOrAfter(LocalDate date, LocalDateTime point) {
		return dateTimeIsOnOrAfter(date.atTime(point.toLocalTime()), point);
	}

	public static boolean dateTimeIsOnOrAfter(LocalDateTime date, LocalDateTime point) {
		return date.isEqual(point) || date.isAfter(point);
	}

	public static boolean dateIsOnOrAfter(LocalDate date, LocalDate point) {
		return date.isEqual(point) || date.isAfter(point);
	}

	public static boolean dateTimeIsOnOrBefore(LocalDate date, LocalDateTime point) {
		return dateTimeIsOnOrBefore(date.atTime(point.toLocalTime()), point);
	}

	public static boolean dateTimeIsOnOrBefore(LocalDateTime date, LocalDateTime point) {
		return date.isEqual(point) || date.isAfter(point);
	}

	public static boolean dateIsOnOrBefore(LocalDate date, LocalDate point) {
		return date.isEqual(point) || date.isBefore(point);
	}

	public static boolean timeIsOnOrAfter(LocalTime time, LocalTime point) {
		return time.equals(point) || time.isAfter(point);
	}

	public static boolean timeIsOnOrBefore(LocalTime time, LocalTime point) {
		return time.equals(point) || time.isBefore(point);
	}

	public static Stream<LocalDate> getDailyLocalDateStreamInclusive(LocalDate start, LocalDate end) {
		return start.datesUntil(end.plusDays(1));
	}

	public static Stream<LocalDate> getDailyLocalDateStreamInclusiveWeekdayOnly(LocalDate start, LocalDate end) {
		return getDailyLocalDateStreamInclusive(start, end).filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY);
	}

	public static Stream<LocalDate> getWeeklyLocalDateStreamInclusive(LocalDate start, LocalDate end) {
		return start.datesUntil(end.plusWeeks(1), Period.ofWeeks(1));
	}
}
