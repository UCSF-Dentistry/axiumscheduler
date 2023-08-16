package ucsf.sod.xo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import ucsf.sod.objects.GenericPeriod;
import ucsf.sod.objects.GenericSession;
import ucsf.sod.objects.Period;
import ucsf.sod.objects.StudentYear;
import ucsf.sod.util.ExcelUtil;
import ucsf.sod.util.SODDateUtils;
import ucsf.sod.xo.calendar.AcademicCalendar;
import ucsf.sod.xo.objects.GroupPractice;
import ucsf.sod.xo.objects.PerioSession;
import ucsf.sod.xo.objects.Rotation;
import ucsf.sod.xo.objects.Student;
import ucsf.sod.xo.objects.Student.Cluster;
import ucsf.sod.xo.objects.Student.PerioGroup;
import ucsf.sod.xo.objects.Student.UpperLower;

public abstract class XOGridReader {
	
	protected final Sheet source;
	protected final Map<Student, Cell> schedule = new TreeMap<Student, Cell>();
	protected final Map<LocalDate, Cell> dateToIndex = new TreeMap<LocalDate, Cell>();
	protected final Set<LocalDate> d4Lecture = new TreeSet<LocalDate>();
	protected final Set<LocalDate> d3Lecture = new TreeSet<LocalDate>();
	protected final Set<LocalDate> huddles = new TreeSet<LocalDate>();
	
	protected static class ReaderConfig {
		public int practiceRowIndex;
		public int clusterRowIndex;
		public int podRowIndex;
		public int studentUpperRowIndex;
		public int studentLowerRowIndex;
		public int isoRowIndex;
		public int firstDateRowIndex;
		public int lastDateRowIndex;
		public Sheet d2Links = null;
		public final NamesLookup nameLookup;
		
		@Deprecated
		public ReaderConfig() throws IOException {
			nameLookup = NamesLookup.read("coaching-20212022.txt");
		}
		
		public ReaderConfig(Sheet names, Sheet links) {
			nameLookup = NamesLookup.read(names);
			d2Links = links;
		}
	}
	protected final ReaderConfig config;

	protected static class Name {
		public final String first;
		public final String last;
		public final String email;
		
		Name(String first, String last, String email) {
			this.first = first;
			this.last = last;
			this.email = email;
		}
	}
	
	protected static class NamesLookup {
		
		private Map<String, Name> names;
		private NamesLookup(Map<String, Name> names) {
			this.names = names;
		}
		
		public Name getName(String id) {
			return names.get(id);
		}
		
		public static NamesLookup read(String file) throws IOException {
			System.out.println("Reading in Student ID to full name mapping in " + file);
			Map<String, Name> names = new HashMap<String, Name>();
			try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
				String line;
				while((line = reader.readLine()) != null) {
					String[] _l = line.split("\t");
					switch(_l.length) {
					case 3:
						names.put(_l[0], new Name(_l[2], _l[1], "none@example.com"));
						break;
					case 5:
						names.put(_l[0], new Name(_l[2], _l[1], _l[3]));
						break;
					default:
						throw new RuntimeException("Malformed line: " + line);						
					}
				}
			}
			
			return new NamesLookup(names);
		}
		
		public static NamesLookup read(Sheet s) {
			
			final int column_studentId = 0;
			final int column_last = 1;
			final int column_first = 2;
			final int column_email = 3;
			
			Map<String, Name> names = new HashMap<String, Name>();
			for(Row r : s) {
				if(r.getRowNum() == 0)
					continue;
				
				String studentId = r.getCell(column_studentId).getStringCellValue();
				String last = r.getCell(column_last).getStringCellValue();
				String first = r.getCell(column_first).getStringCellValue();
				
				String email = "none@example.com";
				{
					Cell c = r.getCell(column_email);
					if(c != null && c.getCellType() == CellType.STRING) {
						email = c.getStringCellValue();
					}
				}
				
				names.put(studentId, new Name(first, last, email));
			}
			return new NamesLookup(names);
		}
	}
	
	protected XOGridReader(Workbook wb, ReaderConfig config) throws IOException {
		source = wb.getSheetAt(0);
		this.config = config;

		Map<String, Pair<PerioSession, PerioGroup>> perio = getPerioInfo();
		
		// Get the column indices that delineate the start of the next group practice
		practiceLookup = generatePracticeLookup(config.practiceRowIndex);
	
		// Get the column indices that delineate the start of the next cluster
		clusterLookup = generateClusterLookup(config.clusterRowIndex);
		System.out.println("Practice and Cluster Information Obtained");
		
		// Pair all the students together
		pairStudents(
			config.nameLookup,
			perio,
			source.getRow(config.podRowIndex), 
			source.getRow(config.studentUpperRowIndex),
			source.getRow(config.studentLowerRowIndex),
			source.getRow(config.isoRowIndex)
		);
		System.out.println("Completed pairing students in same year");
		
		// Build the links
		linkStudents();
		if(config.d2Links != null) {
			linkD2Students(config.nameLookup, config.d2Links);
		}
		
		System.out.println("Completed linking students across years");

		// Read the D3, D4, and Huddle Dates
		readLectureHuddles(config.firstDateRowIndex, config.lastDateRowIndex);
		System.out.println("Collected lecture dates");
		
		// Initialize populate the rotation schedule for each student
		Set<Student> completed = new HashSet<Student>();
		Map<Student, List<LocalDate>> unknownAssignment = new TreeMap<Student, List<LocalDate>>();
		for(Student s : schedule.keySet()) {
			if(completed.contains(s)) {
				continue;
			}
			
			int colIndex = schedule.get(s).getColumnIndex();
			Student partner = s.getPartner();

			for(LocalDate date : dateToIndex.keySet()) {
				Cell c = dateToIndex.get(date).getRow().getCell(colIndex);
				String entry = XOGridUtils.df.formatCellValue(c).toUpperCase();
				Rotation r = Rotation.toRotation(entry);
				
				if(r == Rotation.UNKNOWN) {
					List<LocalDate> l = unknownAssignment.get(s);
					if(l == null) {
						unknownAssignment.put(s, l = new ArrayList<LocalDate>());
					}
					l.add(date);
					
					if(partner != Student.PLACEHOLDER) {
						l = unknownAssignment.get(partner);
						if(l == null) {
							unknownAssignment.put(partner, l = new ArrayList<LocalDate>());
						}
						l.add(date);
					}
				} else if(r == Rotation.CLINIC) {
					r.registerStudent(s, date);
					if(partner != Student.PLACEHOLDER) {
						r.registerStudent(partner, date);
					}
				} else if(XOGridUtils.ROTATION_UPPER_ONLY.matcher(entry).matches()) {
					int rowIndex = schedule.get(s).getRowIndex();
					if(rowIndex == config.studentUpperRowIndex) {
						r.registerStudent(s, date);
						if(partner != Student.PLACEHOLDER) {
							Rotation.CLINIC.registerStudent(partner, date);
						}
					} else if(partner != Student.PLACEHOLDER){
						r.registerStudent(partner, date);
						Rotation.CLINIC.registerStudent(s, date);
					}
				} else if(XOGridUtils.ROTATION_LOWER_ONLY.matcher(entry).matches()) {
					int rowIndex = schedule.get(s).getRowIndex();
					if(rowIndex == config.studentUpperRowIndex) {
						Rotation.CLINIC.registerStudent(s, date);
						if(partner != Student.PLACEHOLDER) {
							r.registerStudent(partner, date);
						}
					} else if(partner != Student.PLACEHOLDER){
						r.registerStudent(s, date);
						if(partner != Student.PLACEHOLDER) {
							Rotation.CLINIC.registerStudent(partner, date);
						}
					}
				} else {
					r.registerStudent(s, date);
					if(partner != Student.PLACEHOLDER) {
						r.registerStudent(partner, date);
					}
				}
			}
			
			completed.add(s);
			if(partner != Student.PLACEHOLDER) {
				completed.add(partner);
			}
		}
		
		if(unknownAssignment.size() != 0) {
			System.err.println("Unknown assignments detected");
			for(Student s : unknownAssignment.keySet()) {
				System.err.println(s.id + "\t" + unknownAssignment.get(s));
			}
		}
	}
	
	public abstract AcademicCalendar getAcademicCalendar();
	
	protected Map<String, Pair<PerioSession, PerioGroup>> getPerioInfo() throws IOException {
		return new HashMap<String, Pair<PerioSession, PerioGroup>>();
	}
	
	protected void pairStudents(NamesLookup names, Map<String, Pair<PerioSession, PerioGroup>> perio, Row podIdentifier, Row studentPair1, Row studentPair2, Row isoIdentifier) {
		
		int limit = studentPair1.getLastCellNum();
		if(AcademicCalendar.CURRENT_YEAR == AcademicCalendar.AY2022_2023) {
			limit = 114;
		}
		int index = 0;
		while(index < limit) {
			index++;
			
			Student a = Student.PLACEHOLDER;
			Cell cell_A = studentPair1.getCell(index);
			String id_A = XOGridUtils.df.formatCellValue(cell_A);			
			if(id_A.length() != 0 && Student.STUDENT_ID_PATTERN.matcher(id_A).matches()) {
				String studentId = sanitizeStudentID(id_A);
				Name name = names.getName(studentId);
				Pair<PerioSession, PerioGroup> p = perio.getOrDefault(id_A, Pair.of(null, null));				
				a = Student.createStudent(studentId,
					name.first,
					name.last,
					name.email,
					getGroupPractice(index),
					getCluster(index),
					extractPod(podIdentifier, index), 
					extractISO(isoIdentifier, index),
					p.getLeft(),
					p.getRight(),
					UpperLower.UPPER
				);
				if(a != Student.PLACEHOLDER) {
					schedule.put(a, cell_A);
				}
			}
			
			Student b = Student.PLACEHOLDER;
			Cell cell_B = studentPair2.getCell(index);
			String id_B = XOGridUtils.df.formatCellValue(cell_B);
			if(id_B.length() != 0 && Student.STUDENT_ID_PATTERN.matcher(id_B).matches()) {
				String studentId = sanitizeStudentID(id_B);
				Name name = names.getName(studentId);
				Pair<PerioSession, PerioGroup> p = perio.getOrDefault(id_B, Pair.of(null, null)); 
				b = Student.createStudent(studentId, 
					name.first,
					name.last,
					name.email,
					getGroupPractice(index), 
					getCluster(index), 
					extractPod(podIdentifier, index), 
					extractISO(isoIdentifier, index),
					p.getLeft(),
					p.getRight(),
					UpperLower.LOWER
				);
				if(b != Student.PLACEHOLDER) {
					schedule.put(b, cell_B);
				}
			}
			
			Student.pair(a, b);
		}
	}
	
	protected String sanitizeStudentID(String id) {
		return "S" + id;
	}
	
	protected char extractPod(Row podIdentifier, int index) {
		return XOGridUtils.df.formatCellValue(podIdentifier.getCell(index)).charAt(0);
	}
	
	protected GenericSession extractISO(Row isoIdentifier, int index) {
		return GenericSession.toSession(XOGridUtils.df.formatCellValue(isoIdentifier.getCell(index)));
	}
	
	protected void linkD2Students(NamesLookup names, Sheet s) {
		throw new RuntimeException("Method is not implemented");
	}
	
	protected void linkStudents() {
		for(Cell c : schedule.values()) {
			Student s = Student.getStudent("S" + XOGridUtils.df.formatCellValue(c));
			if(s.getPrimaryLink() != Student.PLACEHOLDER) { // student has already been linked, move to the next student
				continue;
			}
			
			int index = c.getColumnIndex();
			boolean moveRight = s.year == StudentYear.FOURTH_YEAR;
			String s2_id;
			do {
				s2_id = XOGridUtils.df.formatCellValue(c.getRow().getCell(index + (moveRight ? 1 : -1)));
				if(moveRight) {
					index++;
				} else {
					index--;
				}
			}
			while("".equals(s2_id));
			Student.primaryLink(s, Student.getStudent("S"+s2_id));
		}
	}
	
	protected abstract void readLectureHuddles(int firstDateRowIndex, int lastDateRowIndex) throws IOException;
	
	@Deprecated protected final Map<LocalDate, Map<GenericPeriod, Set<Student>>> npeRotation = new HashMap<LocalDate, Map<GenericPeriod, Set<Student>>>();
	@Deprecated protected final Map<LocalDate, Map<GenericPeriod, Set<Student>>> erRotation = new HashMap<LocalDate, Map<GenericPeriod, Set<Student>>>();

	/*
	 * 		SEARCH FUNCTIONS
	 */
	
	public abstract Map<Student, List<Pair<LocalDate, GenericPeriod>>> getCOVIDSchedule();
	
	public abstract Rotation getRotationByStudent(Student s, LocalDate date, Period p);
	//public abstract boolean isOnRotation(Student s, LocalDate date, Period p);
	
	public Map<LocalDate, Rotation> getAcademicSchedule(Student s, LocalDate start, LocalDate end) {
		Map<LocalDate, Rotation> schedule = new HashMap<LocalDate, Rotation>();
		for(LocalDate date = SODDateUtils.floorToLastMonday(start); date.isBefore(end); date = date.plusDays(7)) {
			Rotation r = getRotationByStudent(s, date, null);
			schedule.put(date, r);
		}
		return schedule;
	}
	
	// TODO: fix this to be abstract and require subclasses to implement
	public LocalDate getD4EarliestExit() {
		throw new RuntimeException("Unimplemented method");
	}
	
	// TODO: fix this to be abstract and require subclasses to implement
	public LocalDate getID3ISOStart() {
		throw new RuntimeException("Unimplemented method");
	}
	
	public boolean isD3LectureDate(LocalDate date) {
		return d3Lecture.contains(date);
	}
	
	// TODO: fix the dates to a quarter in the academic calendar
	public boolean isID3LectureDate(LocalDate date, Period p) {
		
		int startYear;
		int endYear;
		{
			AcademicCalendar calendar = getAcademicCalendar();
			startYear = calendar.startYear;
			endYear = calendar.endYear;
		}
		
		// DENT SCI 126
		if(date.getDayOfWeek() == DayOfWeek.TUESDAY && p.isPM() && SODDateUtils.dateIsOnOrAfter(date, LocalDate.of(startYear, Month.OCTOBER, 1)) && SODDateUtils.dateIsOnOrBefore(date, LocalDate.of(startYear, Month.DECEMBER, 17))) {
			return true;
			
		// PRDS 104
		} else if(date.getDayOfWeek() == DayOfWeek.FRIDAY && p.isAM() && SODDateUtils.dateIsOnOrAfter(date, LocalDate.of(startYear, Month.OCTOBER, 1)) && SODDateUtils.dateIsOnOrBefore(date, LocalDate.of(startYear, Month.DECEMBER, 17))) {
			return true;
			
		// BMS 127
		} else if(date.getDayOfWeek() == DayOfWeek.THURSDAY && p.isAM() && SODDateUtils.dateIsOnOrAfter(date, LocalDate.of(endYear, Month.JANUARY, 3)) && SODDateUtils.dateIsOnOrBefore(date, LocalDate.of(endYear, Month.MARCH, 18))) {
			return true;
		}
		
		return false;
	}
	
	public boolean isD4LectureDate(LocalDate date) {
		return d4Lecture.contains(date);		
	}
	
	public boolean isID4LectureDate(LocalDate date) {
		return isD4LectureDate(date);
	}
	
	public boolean isHuddleDate(LocalDate date) {
		return huddles.contains(date);
	}
	
	public abstract boolean inHuddleDate(GroupPractice practice, LocalDate date, Period p);
	
	public boolean inLecture(Student s, LocalDate date, Period p) {
		// Check if there is lecture
		if(s.isID3() && isID3LectureDate(date, p)) {
			return true;
		} else if(date.getDayOfWeek() == DayOfWeek.FRIDAY) {
			if(s.isD3() || s.isID3()) {
				return d3Lecture.contains(date) && p.isPM();
			} else if(s.isD4() || s.isID4()) {
				return d4Lecture.contains(date) && p.isAM();
			}
		}
		
		return false;
	}
	
	// TODO: make this abstract
	public LocalDate getD3PerioLectureBegins() {
		throw new RuntimeException("Not implemented");
	}
	
	// TODO: make this abstract
	public LocalDate getD3PerioClinicBegins() {
		throw new RuntimeException("Not implemented");
	}

	// TODO: make this abstract
	public LocalDate getID3PerioClinicBegins() {
		throw new RuntimeException("Not implemented");
	}

	/**
	 * Gives the first date by which D2s can provide in clinic
	 * @return
	 */
	public LocalDate getD2ClinicBegins() {
		throw new RuntimeException("Not implemented");
	}
	
	/**
	 * Gives the first date of the Spring Quarter
	 */
	public LocalDate getSpringQuarterStart() {
		throw new RuntimeException("Not implemented");
	}

	public boolean inD3Perio(Student s, LocalDate date, Period p) {
		if(s.isD3() && getRotationByStudent(s, date, p) == Rotation.CLINIC) {
			return s.d3perio == PerioSession.toSession(date, p);
		}
		return false;
	}
	
	public boolean inID3Perio(Student s, LocalDate date, Period p) {
		if(s.isID3() && getRotationByStudent(s, date, p) == Rotation.CLINIC) {
			return s.d3perio == PerioSession.toSession(date, p);
		}
		return false;
	}
	
	@Deprecated
	public boolean isUpperStudent(Student s) {
		if(!schedule.containsKey(s)) {
			return false;
		}
		
		return schedule.get(s).getRowIndex() == config.studentUpperRowIndex;
	}
	
	public boolean isLowerStudent(Student s) {
		if(!schedule.containsKey(s)) {
			return false;
		}
		return schedule.get(s).getRowIndex() == config.studentLowerRowIndex;
	}
	
	@Deprecated
	/**
	 * Use getPriority(LocalDate date) instead
	 */
	public boolean isUpperPriority(LocalDate date) {
		return dateToIndex.get(SODDateUtils.floorToLastMonday(date)).getRow().getCell(1).getStringCellValue().equals("U");
	}
	
	public UpperLower getPriority(LocalDate date) {
		
		String value;
		{
			Cell c = dateToIndex.get(SODDateUtils.floorToLastMonday(date));
			if(c == null) {
				return UpperLower.UNKNOWN;
			}
			value = c.getRow().getCell(1).getStringCellValue();
		}

		if(value.equals("U")) {
			return UpperLower.UPPER;
		} else if(value.equals("L")) {
			return UpperLower.LOWER;
		}
		
		return UpperLower.UNKNOWN;
	}

	public int getRotationISOScheme(LocalDate date) {
		throw new RuntimeException("Unimplemented method");
	}
	
	protected boolean isBreakColor(String color) {
		return "8080:8080:8080".equals(color) || "9696:9696:9696".equals(color) || "C0C0:C0C0:C0C0".equals(color);
	}
	
	public boolean isClinicBreak(LocalDate date) {
		return isBreakColor(ExcelUtil.getForegroundColor(dateToIndex.get(SODDateUtils.floorToLastMonday(date))).getHexString());
	}
	
	public LocalDate getFirstDate() {
		return SODDateUtils.floorToLastMonday(dateToIndex.keySet().stream().collect(Collectors.minBy(Comparator.naturalOrder())).get());
	}
	
	public LocalDate getLastDate() {
		return SODDateUtils.ceilingToDayOfWeek(dateToIndex.keySet().stream().collect(Collectors.maxBy(Comparator.naturalOrder())).get(), DayOfWeek.FRIDAY);
	}
	
	protected int[] generateClusterLookup(int clusterRowIndex) {
		return source.getMergedRegions()
			.stream()
			.filter(a -> a.containsRow(clusterRowIndex))
			.sorted((CellRangeAddress a1, CellRangeAddress a2) -> (a1.getFirstColumn() < a2.getFirstColumn() ? -1 : 1))
			.mapToInt(a -> (a.getLastColumn() + 1))
			.toArray();
	}
	
	protected int[] clusterLookup;
	protected abstract Cluster getCluster(int index);
	
	protected int[] generatePracticeLookup(int practiceRowIndex) {
		 return source.getMergedRegions()
			.stream()
			.filter(a -> a.containsRow(practiceRowIndex))
			.sorted((CellRangeAddress a1, CellRangeAddress a2) -> (a1.getFirstColumn() < a2.getFirstColumn() ? -1 : 1))
			.mapToInt(a -> (a.getLastColumn() + 1))
			.toArray();
	}
	
	protected int[] practiceLookup;
	protected abstract GroupPractice getGroupPractice(int index);
	
	public Map<GroupPractice, List<LocalDate>> getHuddleDatesAll() {
		Map<GroupPractice, List<LocalDate>> huddle = XOGridUtils.createGroupPracticeEnumMap();
		
		GroupPractice practice;
		for(LocalDate date : huddles) {
			switch(date.getDayOfWeek()) {
				case MONDAY: practice = GroupPractice.E; break;
				case TUESDAY: practice = GroupPractice.D; break;	
				case WEDNESDAY:
					if(isHuddleDate(date.plusWeeks(1))) {
						practice = GroupPractice.A;
					} else if(isHuddleDate(date.minusWeeks(1))) {
						practice = GroupPractice.C;
					} else {
						throw new RuntimeException("Unexpected date for huddle: " + date + "[" + date.getDayOfWeek() + "]");
					}
					break;
				case THURSDAY:
					if(isHuddleDate(date.plusWeeks(1))) {
						practice = GroupPractice.F;
					} else if(isHuddleDate(date.minusWeeks(1))) {
						practice = GroupPractice.B;
					} else {
						throw new RuntimeException("Unexpected date for huddle: " + date + "[" + date.getDayOfWeek() + "]");
					}
					break;
				default:
					throw new RuntimeException("Unexpected date for huddle: " + date + "[" + date.getDayOfWeek() + "]");
			}
			
			List<LocalDate> l = huddle.get(practice);
			if(l == null) {
				huddle.put(practice, l = new ArrayList<LocalDate>());
			}
			l.add(date);
		}
		return huddle;
	}
	
	public Set<LocalDate> getD3LectureDates() {
		return new TreeSet<LocalDate>(d3Lecture);
	}

	public Set<LocalDate> getD4LectureDates() {
		return new TreeSet<LocalDate>(d4Lecture);
	}
}
