package ucsf.sod.xo.objects;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ucsf.sod.util.SODDateUtils;

public enum Rotation {
	ZSFGH("OS-ZSFGH", RotationLength.WEEK_LONG) {

		@Override
		public void registerStudents(LocalDate date, List<Student> s) {
			super.registerStudents(date, s);
			throw new RuntimeException("Need to determine how to detect open slot");
		}

		@Override
		public void removeStudents(LocalDate date, List<Student> s) {
			if(rotationSchedule.get(date.minusWeeks(1)).containsAll(s)) {
				super.removeStudents(date.minusWeeks(1), s);
			} else if(rotationSchedule.get(date.plusWeeks(1)).containsAll(s)) {
				super.removeStudents(date.plusWeeks(1), s);
			} else {
				throw new RuntimeException("Unable to find student despite it being a multi week rotation");
			}
			super.removeStudents(date, s);
		}
	},
	D1201("OS-D1201", RotationLength.WEEK_LONG),
	B("B", RotationLength.WEEK_LONG),
	PROS("PROS", RotationLength.WEEK_LONG),
	ENDO("ENDO", RotationLength.WEEK_LONG),
	OM("OM", RotationLength.WEEK_LONG),
	ORTHO("ORTHO", RotationLength.WEEK_LONG),
	PGA("PGA", RotationLength.WEEK_LONG),
	HD("HD", RotationLength.WEEK_LONG),
	XRAY("X-RAY", RotationLength.WEEK_LONG),
	EXT("EXT", RotationLength.WEEK_LONG),
	PEDS("PEDS", RotationLength.WEEK_LONG),
	@Deprecated	COVID("COVID", RotationLength.WEEK_LONG),
	CLINIC("X", RotationLength.WEEK_LONG),
	
	PCC("PCC", RotationLength.SINGLE_DAY),
	PRDS("PRDS", RotationLength.SINGLE_DAY),
	BMS("BMS", RotationLength.SINGLE_DAY),
	DENTSCI("DENTSCI", RotationLength.SINGLE_DAY),
	ISO("ISO", RotationLength.SINGLE_DAY),
	
	UNKNOWN("UNKNOWN", RotationLength.WEEK_LONG) {
		@Override
		public void registerStudent(Student s, LocalDate date) {
			throw new RuntimeException("Cannot register student with UNKNOWN rotation");
		}
		
		@Override
		public Map<LocalDate, Set<Student>> getRotationSchedule() {
			throw new RuntimeException("There is no rotaiton schedule with UNKNOWN");
		}
	};
	
	protected final Map<LocalDate, Set<Student>> rotationSchedule = new HashMap<LocalDate, Set<Student>>();
	private final Set<Student> students = new HashSet<Student>();
	public final RotationLength length;
	public final String label;
	Rotation(String label, RotationLength length) {
		this.label = label;
		this.length = length;
	}
	
	public void registerStudent(Student s, LocalDate date) {
		if(length == RotationLength.WEEK_LONG && date.getDayOfWeek() != DayOfWeek.MONDAY) {
			date = SODDateUtils.floorToLastMonday(date);
		}
		
		Set<Student> l = rotationSchedule.get(date);
		if(l == null) {
			rotationSchedule.put(date, l = new HashSet<Student>());
		}
		l.add(s);
		students.add(s);
	}
	
	public Set<Student> getStudents(LocalDate date) {
		if(length == RotationLength.WEEK_LONG && date.getDayOfWeek() != DayOfWeek.MONDAY) {
			date = SODDateUtils.floorToLastMonday(date);
		}

		Set<Student> l = rotationSchedule.get(date);
		if(l != null) {
			return Collections.unmodifiableSet(l);
		} else {
			return Set.of();
		}
	}
	
	public void registerStudents(LocalDate date, List<Student> s) {
		for(Student _s : s) {
			registerStudent(_s, date);
		}
	}
	
	public void removeStudents(LocalDate date, List<Student> s) {
		
		s = s.stream().filter(_s -> _s != Student.PLACEHOLDER).collect(Collectors.toList());
		
		Set<Student> l = rotationSchedule.get(date);
		if(l == null || l.size() == 0) {
			throw new RuntimeException("No students to remove");
		}
		
		if(!l.removeAll(s)) {
			throw new RuntimeException("Student needs to be removed but was not");
		}
		students.removeAll(s);
	}
	
	public Set<Student> getStudentsByPractice(GroupPractice p, LocalDate date) {
		if(date.getDayOfWeek() != DayOfWeek.MONDAY) {
			date = SODDateUtils.floorToLastMonday(date);
		}
		return getStudentsByPractice(p).getOrDefault(date, Set.of());
	}
	
	public Map<LocalDate, Set<Student>> getStudentsByPractice(GroupPractice p) {
		return rotationSchedule.entrySet().stream()
			.map(e -> Map.entry(e.getKey(), e.getValue().stream().filter(s -> s.practice == p).collect(Collectors.toSet())))
			.filter(e -> e.getValue().size() != 0)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}
	
	// TODO: make the lists inside the map unmodifiable
	public Map<LocalDate, Set<Student>> getRotationSchedule() {
		return Collections.unmodifiableMap(rotationSchedule);
	}
	
	public static Rotation toRotation(String entry) {
		if(entry == null || entry.length() == 0) {
			System.err.println("Argument is null or empty: " + entry);
			return UNKNOWN;
		}
		
		entry = entry.toUpperCase();
		if(entry.contains("PEDS")) {
			return PEDS;
		} else if(entry.contains("PROS")) {
			return PROS;
		} else if(entry.contains("EXT")) {
			return EXT;
		} else if(entry.contains("ORAL RAD")) {
			return XRAY;
		} else if(entry.contains("ENDO")) {
			return ENDO;
		} else if(entry.contains("PERIO")) {
			return PGA;
		}
		
		for(Rotation a : values()) {
			if(entry.contains(a.label)) {
				return a;
			}
		}
		
		System.err.println("Argument is unrecognized: " + entry);
		return UNKNOWN;
	}
	
	public static enum RotationLength {
		SINGLE_DAY,
		WEEK_LONG
	}
}