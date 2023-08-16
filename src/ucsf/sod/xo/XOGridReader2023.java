package ucsf.sod.xo;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import ucsf.sod.objects.GenericPeriod;
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

public class XOGridReader2023 extends XOGridReader {

	// Zero-based counting
	private static final int PRACTICE_ROW_INDEX = 3;
	private static final int CLUSTER_ROW_INDEX = 4;
	private static final int POD_ROW_INDEX = 5;
	private static final int STUDENT_UPPER_ROW_INDEX = 6;
	private static final int STUDENT_LOWER_ROW_INDEX = 7;
	private static final int ISO_ROW_INDEX = 9;
	private static final int D3_PERIO_INDEX = 10;
	
	// Zero-based counting
	private static final int FIRST_DATE_ROW_INDEX = 12;
	private static final int LAST_DATE_ROW_INDEX = 63;
	private static final int LEGEND_GENERAL_ROW_START_INDEX = 65;
	private static final int LEGEND_LECTURE_COL_INDEX = 16;
	
	// Special Dates of Interest
	// TODO: figure out how to not hardcode these dates
	private static final LocalDate pcc132Lecture = LocalDate.of(2023, Month.SEPTEMBER, 29);
	private static final LocalDate d4EarliestExit = LocalDate.of(2024, Month.JUNE, 1);
	private static final LocalDate id3ISOStart = LocalDate.of(2024, Month.JANUARY, 1);
	private static final LocalDate id3ProvidingStart = LocalDate.of(2023, Month.OCTOBER, 16);
	private static final LocalDate d3PerioLectureBegins = LocalDate.of(2023, Month.JULY, 10);
	private static final LocalDate d3PerioClinicBegins = LocalDate.of(2023, Month.JULY, 17);
	private static final LocalDate id3PerioClinicBegins = LocalDate.of(2023, Month.OCTOBER, 23);
	private static final LocalDate d2ClinicBegins = LocalDate.of(2024, Month.APRIL, 15);
	private static final LocalDate springQuarterStart = LocalDate.of(2024, Month.APRIL, 8);
	
	// All times in the PM
	private static final Set<LocalDate> id3_IPHE = Set.of(
		LocalDate.of(2023, Month.OCTOBER, 16),	// Session #1
		LocalDate.of(2024, Month.JANUARY, 8),	// Session #2
		LocalDate.of(2024, Month.APRIL, 15)		// Session #3
	);
	
	// All times in the PM
	private static final Set<LocalDate> id4_IPHE = Set.of(
		LocalDate.of(2023, Month.OCTOBER, 2),
		LocalDate.of(2023, Month.DECEMBER, 4)
	);
	
	public static XOGridReader2023 of(Workbook wb) throws IOException {
		ReaderConfig config = new ReaderConfig(wb.getSheet("Names"), wb.getSheet("Links"));
		config.practiceRowIndex = PRACTICE_ROW_INDEX;
		config.clusterRowIndex = CLUSTER_ROW_INDEX;
		config.podRowIndex = POD_ROW_INDEX;
		config.studentUpperRowIndex = STUDENT_UPPER_ROW_INDEX;
		config.studentLowerRowIndex = STUDENT_LOWER_ROW_INDEX;
		config.isoRowIndex = ISO_ROW_INDEX;
		config.firstDateRowIndex = FIRST_DATE_ROW_INDEX;
		config.lastDateRowIndex = LAST_DATE_ROW_INDEX;
		return new XOGridReader2023(wb, config);
	}
	
	private XOGridReader2023(Workbook wb, ReaderConfig config) throws IOException {
		super(wb, config);
	}

	@Override
	protected String sanitizeStudentID(String id) {
		return id;
	}

	@Override
	public AcademicCalendar getAcademicCalendar() {
		return AcademicCalendar.AY2023_2024;
	}

	@Override
	protected void linkStudents() {
		for(Cell c : schedule.values()) {
			Student s = Student.getStudent(XOGridUtils.df.formatCellValue(c));
			if(s == null) {
				throw new RuntimeException("Not a student: " + c);
			} else if(s.getPrimaryLink() != Student.PLACEHOLDER) { // student has already been linked, move to the next student
				continue;
			}
			
			int index = c.getColumnIndex();
			boolean moveRight = s.year == StudentYear.FOURTH_YEAR;
			String s2_id;
			s2_id = XOGridUtils.df.formatCellValue(c.getRow().getCell(index + (moveRight ? 1 : -1)));
			if(moveRight) {
				index++;
			} else {
				index--;
			}
			
			if(s2_id.length() == 0) {
				System.err.println(s + " does not have a link");
			} else {
				Student.primaryLink(s, Student.getStudent(s2_id));
			}
		}
	}
	
	@Override
	protected void linkD2Students(NamesLookup names, Sheet s) {
		for(Row r : s) {
			
			Student d4 = Student.PLACEHOLDER;
			Student d3 = Student.PLACEHOLDER;
			Student d2;
			
			// Find all the students
			{
				String d2Id = null;
				for(Cell c : r) {
					String studentId = c.getStringCellValue();
					switch(c.getAddress().getColumn()) {
					case 0:
						d4 = Student.getStudent(c.getStringCellValue());
						break;
					case 1:
						d3 = Student.getStudent(c.getStringCellValue());
						break;
					case 2:
						d2Id = studentId;
						break;
					default:
						throw new RuntimeException("There are too many cells in row " + c.getAddress().getRow());
					}
				}

				Student link;
				if(d2Id == null) {
					System.err.println("No D2 found on row " + (r.getRowNum()+1));
					continue;
				} else if(d4 == Student.PLACEHOLDER && d3 == Student.PLACEHOLDER) {
					throw new RuntimeException("D2 is by themselves on row " + (r.getRowNum()+1));
				} else if(d4 != Student.PLACEHOLDER) {
					link = d4;
				} else if(d3 != Student.PLACEHOLDER) {
					link = d3;
				} else {
					throw new RuntimeException("Impossible to reach under normal circumstances in row " + (r.getRowNum()+1));
				}
	
				Name name = names.getName(d2Id);
				d2 = Student.createStudent(d2Id,
					name.first,
					name.last,
					name.email,
					link.practice,
					link.cluster,
					link.pod,
					link.iso,
					null,
					null,
					link.priority
				);
			}			
			
			// Link the D4-D3-D2 together
			if(d4 != Student.PLACEHOLDER) {
				Student.secondaryLink(d4, d2);
			}
			
			if(d3 != Student.PLACEHOLDER) {
				Student.secondaryLink(d3, d2);
			}
		}
	}
	
	@Override
	protected void readLectureHuddles(int firstDateRowIndex, int lastDateRowIndex) throws IOException {
		int index = firstDateRowIndex;
		String[] date = XOGridUtils.df.formatCellValue(source.getRow(index).getCell(0)).split("\\s");
		String day = (date[2].split("-"))[0];
		LocalDate time = LocalDate.of(getAcademicCalendar().startYear, Month.JUNE, Integer.parseInt(day));
		String d4LectureColor = ExcelUtil.getForegroundColor(source.getRow(LEGEND_GENERAL_ROW_START_INDEX+1).getCell(LEGEND_LECTURE_COL_INDEX)).getHexString();
		String huddleColor = ExcelUtil.getForegroundColor(source.getRow(LEGEND_GENERAL_ROW_START_INDEX+2).getCell(LEGEND_LECTURE_COL_INDEX)).getHexString();
		String jointColor = ExcelUtil.getForegroundColor(source.getRow(LEGEND_GENERAL_ROW_START_INDEX+3).getCell(LEGEND_LECTURE_COL_INDEX)).getHexString();
		
		do {
			//System.out.println(getForegroundColor(source.getRow(index).getCell(0)).getHexString());
			Cell c = source.getRow(index++).getCell(0);
			dateToIndex.put(time, c);
			LocalDate friDate = SODDateUtils.ceilingToDayOfWeek(time, DayOfWeek.FRIDAY);

			String weekColor = ExcelUtil.getForegroundColor(c).getHexString();
			if(d4LectureColor.equals(weekColor)) { // TODO: clean X-O grid to label all weeks with D4 lecture
				d4Lecture.add(friDate);
			} else if(huddleColor.equals(weekColor)) {
				for(LocalDate _time = time; _time.getDayOfWeek() != DayOfWeek.FRIDAY; _time = _time.plusDays(1)) {
					huddles.add(_time);
					if(_time.getDayOfWeek() == DayOfWeek.WEDNESDAY) { // For Group Practice C
						huddles.add(_time.plusWeeks(1));
					} else if(_time.getDayOfWeek() == DayOfWeek.THURSDAY){ // For Group Practice F
						huddles.add(_time.plusWeeks(1));
					}
				}
			} else if(jointColor.equals(weekColor)) {
				d4Lecture.add(friDate);					
				for(LocalDate _time = time; _time.getDayOfWeek() != DayOfWeek.FRIDAY; _time = _time.plusDays(1)) {
					switch(_time.getDayOfWeek()) {
					case WEDNESDAY:
						huddles.add(_time);					// For Group Practice A
						huddles.add(_time.plusWeeks(1)); 	// For Group Practice C
						break;
					case THURSDAY: // For Group Practice B & F
						huddles.add(_time);					// For Group Practice F
						huddles.add(_time.plusWeeks(1)); 	// For Group Practice B
						
						/*
						// TODO: Determine huddle override schedule dates
						AcademicCalendar calendar = getAcademicCalendar();
						if(_time.equals(LocalDate.of(calendar.endYear, Month.JUNE, 1))) {
							huddles.add(_time);
						} else if(_time.equals(LocalDate.of(calendar.startYear, Month.SEPTEMBER, 1))) {
							huddles.add(_time.plusWeeks(2));
						} else {
							huddles.add(_time.plusWeeks(1));
						}
						*/
						break;
					default:
						huddles.add(_time);
					}
				}
			}
			
			// List all the D3 lecture dates. Remember clean X-O grid to match B color
			if(!isBreakColor(weekColor)) {
				if(!getAcademicCalendar().isNotableDate(friDate)) {
					d3Lecture.add(friDate);
				}
			} else if (friDate.equals(pcc132Lecture)) {
				d3Lecture.add(friDate);
			}

			time = time.plusDays(7);
		}
		while(index <= lastDateRowIndex);
	}

	@Override
	protected Map<String, Pair<PerioSession, PerioGroup>> getPerioInfo() throws IOException {
		Map<String, Pair<PerioSession, PerioGroup>> perio = new HashMap<String, Pair<PerioSession, PerioGroup>>();

		Row upper = source.getRow(STUDENT_UPPER_ROW_INDEX);
		Row lower = source.getRow(STUDENT_LOWER_ROW_INDEX);
		Row data = source.getRow(D3_PERIO_INDEX);
		
		int limit = 112; //data.getLastCellNum();
		int index = 0;
		while(index < limit) {
			index++;
			
			Cell d = data.getCell(index);
			String _d = XOGridUtils.df.formatCellValue(d);
			if(_d.length() == 0) {
				continue;
			}
			
			Pair<PerioSession, PerioGroup> session = Pair.of(PerioSession.toSession(_d), PerioGroup.A);
			
			Cell cell_A = upper.getCell(index);
			String id_A = XOGridUtils.df.formatCellValue(cell_A);
			if(id_A.length() != 0 && Student.STUDENT_ID_PATTERN.matcher(id_A).matches()) {
				perio.put(id_A, session);
			}
			
			Cell cell_B = lower.getCell(index);
			String id_B = XOGridUtils.df.formatCellValue(cell_B);
			if(id_B.length() != 0 && Student.STUDENT_ID_PATTERN.matcher(id_B).matches()) {
				perio.put(id_B, session);
			}
		}
		return perio;
	}
	
	@Override
	public Map<Student, List<Pair<LocalDate, GenericPeriod>>> getCOVIDSchedule() {
		throw new RuntimeException("Method not implemented");
	}

	@Override
	public Rotation getRotationByStudent(Student s, LocalDate date, Period p) {
		
		if(s == Student.PLACEHOLDER || s.isD2()) {
			return Rotation.UNKNOWN;
		}
		
		Cell c = dateToIndex.get(SODDateUtils.floorToLastMonday(date));
		if(c == null) {
			return Rotation.UNKNOWN;
		} else {
			String entry = XOGridUtils.df.formatCellValue(c.getRow().getCell(schedule.get(s).getColumnIndex()));
			Rotation r = Rotation.toRotation(entry);
			
			if(XOGridUtils.ROTATION_UPPER_ONLY.matcher(entry).matches() && schedule.get(s).getRowIndex() == STUDENT_LOWER_ROW_INDEX) {
				r = Rotation.CLINIC;
			} else if(XOGridUtils.ROTATION_LOWER_ONLY.matcher(entry).matches() && schedule.get(s).getRowIndex() == STUDENT_UPPER_ROW_INDEX) {
				r = Rotation.CLINIC;
			}
			return r;
		}
	}

	@Override
	public LocalDate getD4EarliestExit() {
		return d4EarliestExit;
	}
	
	/**
	 * Gives the first date of the Srping Quarter
	 */
	@Override
	public LocalDate getSpringQuarterStart() {
		return springQuarterStart;
	}
	
	@Override
	public LocalDate getID3ISOStart() {
		return id3ISOStart;
	}

	@Override
	public boolean isID3LectureDate(LocalDate date, Period p) {
		
		int startYear;
		int endYear;
		{
			AcademicCalendar calendar = getAcademicCalendar();
			startYear = calendar.startYear;
			endYear = calendar.endYear;
		}

		DayOfWeek dayOfWeek = date.getDayOfWeek();
		
		// Fall Quarter
		if(SODDateUtils.dateIsOnOrAfter(date, LocalDate.of(startYear, Month.SEPTEMBER, 25)) && SODDateUtils.dateIsOnOrBefore(date, LocalDate.of(startYear, Month.DECEMBER, 15))) {
			
			switch(dayOfWeek) {
				case TUESDAY:
					// DENT SCI 126
					if(p.isPM()) {
						return true;
					}
					break;
				case THURSDAY:
					// BMS 127
					if(p.isAM()) {
						return true;
					}
					break;
				case FRIDAY:
					// PRDS 104 && PCC132
					return true;
				default:
					// do nothing
			}
		} else if(dayOfWeek == DayOfWeek.FRIDAY && p.isPM()){
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean inLecture(Student s, LocalDate date, Period p) {
		
		// Check for IPHE dates first
		if(s.isID3() && p.isPM() && id3_IPHE.contains(date)) {
			return true;
		} else if(s.isID4() && p.isPM() && id4_IPHE.contains(date)) {
			return true;
		}
		
		return super.inLecture(s, date, p);
	}

	@Override
	public LocalDate getD3PerioLectureBegins() {
		return d3PerioLectureBegins;
	}
	
	@Override
	public LocalDate getD3PerioClinicBegins() {
		return d3PerioClinicBegins;
	}

	@Override
	public LocalDate getID3PerioClinicBegins() {
		return id3PerioClinicBegins;
	}

	@Override
	public LocalDate getD2ClinicBegins() {
		return d2ClinicBegins;
	}

	@Override
	public boolean inD3Perio(Student s, LocalDate date, Period p) {
		if(super.inD3Perio(s, date, p)) {	
			if(SODDateUtils.dateIsOnOrBefore(date, d3PerioLectureBegins)) {
				return false;
			} else if(SODDateUtils.dateIsOnOrAfter(date, d3PerioClinicBegins)) {
				if(isUpperPriority(date) && isUpperStudent(s)) {
					return true;
				} else if(!isUpperPriority(date) && !isUpperStudent(s)) {
					return true;
				}
			} else {
				return true;
			}
		}
		
		return false;
	}

	public boolean inID3Perio(Student s, LocalDate date, Period p) {
		if(super.inID3Perio(s, date, p)) {
			if(SODDateUtils.dateIsOnOrAfter(date, id3PerioClinicBegins)) {
				if(isUpperPriority(date) && isUpperStudent(s)) {
					return true;
				} else if(!isUpperPriority(date) && !isUpperStudent(s)) {
					return true;
				}
			} else {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean inHuddleDate(GroupPractice practice, LocalDate date, Period p) {
		
		if(!isHuddleDate(date) || !p.isAM()) {
			return false;
		}
		
		DayOfWeek huddleDay;
		switch(practice) {
		case A:
		case C:
			huddleDay = DayOfWeek.WEDNESDAY; break;
		case B:
		case F:
			huddleDay = DayOfWeek.THURSDAY; break;
		case D:
			huddleDay = DayOfWeek.TUESDAY; break;
		case E:
			huddleDay = DayOfWeek.MONDAY; break;
		default:
			throw new RuntimeException("Unknown practice: " + practice);
		}
		
		if(date.getDayOfWeek() == huddleDay) {
			boolean passthrough = true;
			if(practice == GroupPractice.A && !isHuddleDate(date.plusWeeks(1))) {
				passthrough = false;
			} else if(practice == GroupPractice.C && !isHuddleDate(date.minusWeeks(1))) {
				passthrough = false;
			} else if(practice == GroupPractice.F && !isHuddleDate(date.plusWeeks(1))) {
				passthrough = false;
			} else if(practice == GroupPractice.B && !isHuddleDate(date.minusWeeks(1))) {
				passthrough = false;
			}
			return passthrough;
		} else {
			return false;
		}
	}

	protected Cluster getCluster(int index) {
		// Group Practice A
		if(index < clusterLookup[0]) {
			return Cluster.C_12;
		} else if(index < clusterLookup[1]) {
			return Cluster.C_34;
		// Group Practice B
		} else if(index < clusterLookup[2]) {
			return Cluster.C_12;
		} else if(index < clusterLookup[3]) {
			return Cluster.C_34;
		// Group Practice C
		} else if(index < clusterLookup[4]) {
			return Cluster.C_12;
		} else if(index < clusterLookup[5]) {
			return Cluster.C_34;
		// Group Practice D
		} else if(index < clusterLookup[6]) {
			return Cluster.C_12;
		} else if(index < clusterLookup[7]) {
			return Cluster.C_34;
		// Group Practice E
		} else if(index < clusterLookup[8]) {
			return Cluster.C_12;
		} else if(index < clusterLookup[9]) {
			return Cluster.C_34;
		}
		return null;
	}
	
	protected GroupPractice getGroupPractice(int index) {
		if(index < practiceLookup[0]) {
			return GroupPractice.A;
		} else if(index < practiceLookup[1]) {
			return GroupPractice.B;
		} else if(index < practiceLookup[2]) {
			return GroupPractice.C;
		} else if(index < practiceLookup[3]) {
			return GroupPractice.D;
		} else if(index < practiceLookup[4]) {
			return GroupPractice.E;
		} else if(index < practiceLookup[5]) {
			return GroupPractice.F;
		}
		return null;
	}
}
