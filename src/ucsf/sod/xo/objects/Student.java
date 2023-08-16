package ucsf.sod.xo.objects;

import static ucsf.sod.xo.calendar.AcademicCalendar.CURRENT_YEAR;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import ucsf.sod.objects.GenericSession;
import ucsf.sod.objects.Session;
import ucsf.sod.objects.StudentYear;
import ucsf.sod.objects.Session.Clinic;
import ucsf.sod.xo.XOGridUtils;
import ucsf.sod.xo.calendar.AcademicCalendar;

public class Student implements Comparable<Student> {
	
	public static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^[IiSs]?[0-9]{4}$");
	public static final Pattern STUDENT_ID_FLOATING_PATTERN = Pattern.compile("[IiSs]?[0-9]{4}");
	private static final Map<String, Student> students = new ConcurrentSkipListMap<String, Student>();

	public final String id;
	public final String email;
	public final StudentProgram program;
	public final StudentYear year;
	public final String first;
	public final String last;
	public final GroupPractice practice;
	public final Cluster cluster;
	public final char pod;
	public final GenericSession iso;
	public final PerioSession d3perio;
	public final PerioGroup perioGroup;
	public final UpperLower priority;

	protected Student d2link;
	protected Student d3link;
	protected Student d4link;
	
	public final LocalDate clinicStartDate;
	
	Student(String id, String first, String last) {
		this(id, first, last, XOGridUtils.EMPTY_EMAIL, null, Cluster.C, '?', null, null, null, UpperLower.NEITHER);
	}
	
	Student(String id, String first, String last, String email, GroupPractice practice, Cluster cluster, char pod, GenericSession iso, PerioSession perio, PerioGroup perioGroup, UpperLower priority) {
		this.id = id;
		if(id.startsWith("S")) {
			this.program = StudentProgram.DOMESTIC;
		} else if(id.startsWith("I")) {
			this.program = StudentProgram.INTERNATIONAL;
		} else {
			this.program = null;
		}
		
		this.year = AcademicCalendar.CURRENT_YEAR.toStudentYear(this);
		clinicStartDate = AcademicCalendar.getClinicStartDate(this);
		
		this.first = first;
		this.last = last;
		this.email = email;
		this.practice = practice;
		if(practice != null) {
			practice.register(this, pod);
		}
		this.cluster = cluster;
		this.pod = pod;
		this.iso = iso;
		this.d3perio = perio;
		this.perioGroup = perioGroup;
		this.priority = priority;
		this.d2link = PLACEHOLDER;
		this.d3link = PLACEHOLDER;
		this.d4link = PLACEHOLDER;
	}
	
	public boolean isUpperStudent() {
		return priority == UpperLower.UPPER;
	}
	
	public boolean isLowerStudent() {
		return priority == UpperLower.LOWER;
	}

	public boolean isThirdYear() {
		return year == StudentYear.THIRD_YEAR;
	}
	
	public boolean isFourthYear() {
		return year == StudentYear.FOURTH_YEAR;
	}
	
	public boolean isD4() {
		return year == StudentYear.FOURTH_YEAR && program == StudentProgram.DOMESTIC;
	}
	
	public boolean isD3() {
		return year == StudentYear.THIRD_YEAR && program == StudentProgram.DOMESTIC;
	}
	
	public boolean isD2() {
		return year == StudentYear.SECOND_YEAR;
	}
	
	public boolean isID4() {
		return year == StudentYear.FOURTH_YEAR && program == StudentProgram.INTERNATIONAL;
	}
	
	public boolean isID3() {
		return year == StudentYear.THIRD_YEAR && program == StudentProgram.INTERNATIONAL;
	}
	
	public boolean isID2() {
		return isD2();
	}

	public boolean hasGraduated() {
		return year == StudentYear.FIFTH_YEAR || year == StudentYear.GRADUATE;
	}
	
	public int getGraduationYear() {
		if(this == Student.PLACEHOLDER || this == Student.NPE || this == Student.ER) {
			return -1;
		}
		
		return 2000 + Integer.parseInt(id.substring(1,3));
	}

	public Student getPartner() {
		switch(year) {
		case FOURTH_YEAR:
			return d4link;
		case THIRD_YEAR:
			return d3link;
		case SECOND_YEAR:
			return PLACEHOLDER;
		default:
			throw new RuntimeException("Student does not have a partner yet.");
		}
	}
	
	public Student getPrimaryLink() {
		switch(year) {
		case FOURTH_YEAR:
			return d3link;
		case THIRD_YEAR:
		case SECOND_YEAR:
			return d4link;
		default:
			throw new RuntimeException("Student does not have a primary link yet.");
		}
	}
	
	public Student getSecondaryLink() {
		switch(year) {
		case FOURTH_YEAR:
		case THIRD_YEAR:
			return d2link;
		case SECOND_YEAR:
			return d3link;
		default:
			throw new RuntimeException("Student does not have a secondary link yet.");
		}
	}

	public boolean equals(Object o) {
		if(o instanceof Student) {
			return id.equals(((Student)o).id);
		}
		return false;
	}
	
	public int hashCode() {
		return id.hashCode();
	}
	
	public String toString() {
		StringBuilder builder = new StringBuilder().append(id)
			.append('(').append(practice == null ? '?' : practice).append('_').append(pod).append(cluster == null ? 'X' : cluster.symbol).append('|').append(priority.toChar()).append(')')
			.append('[').append(iso).append(']')
		.append('{');
		boolean separatorNeeded = false;
		
		if(d4link != PLACEHOLDER) {
			switch(year) {
				case FOURTH_YEAR:
					builder.append("partner=");
					break;
				case THIRD_YEAR:
				case SECOND_YEAR:
					builder.append("link=");
					break;
				case FIRST_YEAR:
				case UNKNOWN:
				case GRADUATE:
					break;
				default:
					throw new RuntimeException("Unknown year: " + year);
			}
			builder.append(d4link.id);
			separatorNeeded = true;
		}

		if(d3link!= PLACEHOLDER) {
			builder.append(separatorNeeded ? "," : "");
			switch(year) {
				case FOURTH_YEAR:
					builder.append("link=");
					break;
				case THIRD_YEAR:
					builder.append("partner=");
					break;
				case SECOND_YEAR:
					builder.append("auxlink=");
					break;
				default:
					throw new RuntimeException("Unknown year: " + year);
			}
			builder.append(d3link.id);
			separatorNeeded = true;
		}
		
		if(d2link!= PLACEHOLDER) {
			builder.append(separatorNeeded ? "," : "");
			switch(year) {
				case FOURTH_YEAR:
				case THIRD_YEAR:
					builder.append("auxlink=");
					break;
				case SECOND_YEAR:
					builder.append("partner=");
					break;
				default:
					throw new RuntimeException("Unknown year: " + year);
			}
			builder.append(d2link.id);
			separatorNeeded = true;
		}
		
		if(d3perio != null) {
			builder.append(separatorNeeded ? "," : "").append("perio=").append(d3perio).append('_').append(perioGroup.name());
			separatorNeeded = true;
		}

		builder.append(separatorNeeded ? "," : "").append("first=").append(first);
		builder.append(",").append("last=").append(last);
		
		return builder.append('}').toString();
	}
	
	public static void pair(Student s1, Student s2) {
		if(s1 == PLACEHOLDER || s2 == PLACEHOLDER) {
			System.err.println("One of the students pairing is a placeholder: " + s1 + "," + s2);
			return;
		} else if(s1.getPartner() != PLACEHOLDER) {
			throw new IllegalArgumentException("Student is already paired: " + s1);
		} else if(s2.getPartner() != PLACEHOLDER) {
			throw new IllegalArgumentException("Student is already paired: " + s2);
		} else if(s1.year != s2.year) {
			throw new IllegalArgumentException("Students are of different years when they should not be: " + s1 + "," + s2);
		}

		switch(s1.year) {
			case FOURTH_YEAR:
				s1.d4link = s2;
				s2.d4link = s1;
				break;
			case THIRD_YEAR:
				s1.d3link = s2;
				s2.d3link = s1;
				break;
			case SECOND_YEAR:
				s1.d2link = s2;
				s2.d2link = s1;
				break;
			default:
				throw new RuntimeException("Pairing year is not supported: " + s1.year);
		}
	}
	
	public static void primaryLink(Student s1, Student s2) {
		if(s1 == null || s2 == null) {
			System.err.println("One of the students linking is null: " + s1 + "," + s2);
			return;
		} else if(s1 == PLACEHOLDER || s2 == PLACEHOLDER) {
			System.err.println("One of the students linking is a placeholder: " + s1 + "," + s2);
			return;
		} else if(s1.getPrimaryLink() != PLACEHOLDER) {
			throw new IllegalArgumentException("Cannot pair " + s2 + " with "+s1+", as "+s1+" is already paired");
		} else if(s2.getPrimaryLink() != PLACEHOLDER) {
			throw new IllegalArgumentException("Cannot pair " + s1 + " with "+s2+", as "+s2+" is already paired");
		} else if(s1.year == s2.year) {
			throw new IllegalArgumentException("Students are of same years when they should not be: " + s1 + "," + s2);
		} else if((s1.year == StudentYear.THIRD_YEAR && s2.year == StudentYear.SECOND_YEAR) || (s1.year == StudentYear.SECOND_YEAR && s2.year == StudentYear.THIRD_YEAR)) {
			throw new IllegalArgumentException("Third years and second years are not primary links: " + s1 + "," + s2);
		}

		switch(s1.year) {
			case FOURTH_YEAR:
				s1.d3link = s2;
				s2.d4link = s1;
				break;
			case THIRD_YEAR:
				s1.d4link = s2;
				s2.d3link = s1;
				break;
			case SECOND_YEAR:
				s1.d4link = s2;
				s2.d2link = s1;
				break;
			default:
				throw new RuntimeException("Pairing year is not supported: " + s1.year);
		}
	}
	
	public static void secondaryLink(Student s1, Student s2) {
		if(s1 == PLACEHOLDER || s2 == PLACEHOLDER) {
			System.err.println("One of the students linking is a placeholder: " + s1 + "," + s2);
			return;
		} else if(s1.getSecondaryLink() != PLACEHOLDER) {
			throw new IllegalArgumentException("Cannot pair " + s2 + " with "+s1+", as "+s1+" is already paired");
		} else if(s2.getSecondaryLink() != PLACEHOLDER) {
			throw new IllegalArgumentException("Cannot pair " + s1 + " with "+s2+", as "+s2+" is already paired");
		} else if(s1.year == s2.year) {
			throw new IllegalArgumentException("Students are of same years when they should not be: " + s1 + "," + s2);
		} else if((s1.year == StudentYear.FOURTH_YEAR && s2.year == StudentYear.THIRD_YEAR) || (s1.year == StudentYear.THIRD_YEAR && s2.year == StudentYear.FOURTH_YEAR)) {
			throw new IllegalArgumentException("Third years and second years are not primary links: " + s1 + "," + s2);
		}

		switch(s1.year) {
			case FOURTH_YEAR:
				s1.d2link = s2;
				s2.d4link = s1;
				break;
			case THIRD_YEAR:
				s1.d2link = s2;
				s2.d3link = s1;
				break;
			case SECOND_YEAR:
				s1.d4link = s2;
				s2.d2link = s1;
				break;
			default:
				throw new RuntimeException("Pairing year is not supported: " + s1.year);
		}
	}
	
	public static Student createLegacyStudent(String id) {
		if(id.length() == 0 || !STUDENT_ID_PATTERN.matcher(id).matches()) {
			throw new IllegalArgumentException("Student ID does not match expected format: " + id);
		}

		StudentYear year = CURRENT_YEAR.toStudentYear(id);
		if(year != StudentYear.FIFTH_YEAR && year != StudentYear.GRADUATE) {
			throw new RuntimeException("ID does not represent a legacy student: " + id);
		} else {
			Student s;
			synchronized(students) {
				if(students.containsKey(id)) {
					s = students.get(id);
				} else {
					students.put(id, s = new Student(id, "#" + id, "Student"));
					System.err.println("Legacy Student created: " + id);
				}
			}
			return s;
		}
	}
	
	public static Student createStudent(String id, String first, String last, String email, GroupPractice practice) {
		return createStudent(id, first, last, email, practice, null, '1', null, null, null, UpperLower.UNKNOWN);
	}

	public static Student createStudent(String id, String first, String last, String email, GroupPractice practice, Cluster cluster, char pod, GenericSession iso, PerioSession d3Perio, PerioGroup perioGroup, UpperLower priority) {

		if(id.length() == 0 || !STUDENT_ID_PATTERN.matcher(id).matches()) {
			throw new IllegalArgumentException("Student ID does not match expected format: " + id);
		}
		
		Student s;
		synchronized(students) {
			if(students.containsKey(id)) {
				throw new RuntimeException("Creating a duplicate student: " + id);
			}
			
			students.put(id, s = new Student(id, first, last, email, practice, cluster, pod, iso, d3Perio, perioGroup, priority));
		}
		return s;
	}
	
	public static Student getStudent(String id) {
		
		if(id == null || id.length() == 0) {
			return null;
		} else if("S2396".equals(id)) {
			return PLACEHOLDER;
		} else if("D099".equals(id)) {
			return ER;
		} else if("D106".equals(id) || "D104".equals(id)) {
			return NPE;
		} else if(Character.isLowerCase(id.charAt(0))) {
			return getStudent(id.toUpperCase());
		}

		if("S2176".equals(id)) {
			id = "S2291";
		} else if("I2321".equals(id) && AcademicCalendar.CURRENT_YEAR == AcademicCalendar.AY2021_2022) {
			id = "I2309";
		}
		
		return students.get(id);
	}
	
	@Deprecated
	public static Student getStudentOrCreate(String id) {
		Student s = getStudent(id);
		if(s == null) {
			StudentYear year = CURRENT_YEAR.toStudentYear(id);
			if(year != StudentYear.FIFTH_YEAR && year != StudentYear.GRADUATE) {
				throw new RuntimeException("Unknown student: " + id);
			} else {
				s = new Student(id, "#" + id, "Student");
				synchronized(students) {
					students.put(id, s);
				}
				System.err.println("Legacy Student created: " + id);
			}
		}
		return s;
	}
	
	public static TreeSet<Student> getStudentsSorted() {
		return students.values().stream().filter(s -> s.year != StudentYear.GRADUATE && s.year != StudentYear.UNKNOWN && s.year != StudentYear.FIFTH_YEAR).collect(Collectors.toCollection(() -> new TreeSet<Student>()));
	}

	public static TreeSet<Student> getStudentsSorted(Predicate<Student> p) {
		return students.values().stream().filter(p).collect(Collectors.toCollection(() -> new TreeSet<Student>()));
	}
	
	public static final Student PLACEHOLDER = new Student("0000", "PLACE", "HOLDER");
	static {
		PLACEHOLDER.d4link = PLACEHOLDER;
		PLACEHOLDER.d3link = PLACEHOLDER;
		PLACEHOLDER.d2link = PLACEHOLDER;
	}

	public static final Student ER = new Student("D099", "ER", "ER");
	static {
		ER.d4link = PLACEHOLDER;
		ER.d3link = PLACEHOLDER;
		ER.d2link = PLACEHOLDER;
	}

	public static final Student NPE = new Student("D106", "NPE", "NPE");
	static {
		NPE.d4link = PLACEHOLDER;
		NPE.d3link = PLACEHOLDER;
		NPE.d2link = PLACEHOLDER;
	}

	public static enum Cluster {
		C_12('^', "1/2", List.of(GenericSession.MONDAY_AM, GenericSession.TUESDAY_AM, GenericSession.TUESDAY_PM, GenericSession.THURSDAY_PM)),
		C_34('v', "3/4", List.of(GenericSession.MONDAY_PM, GenericSession.WEDNESDAY_AM, GenericSession.WEDNESDAY_PM, GenericSession.THURSDAY_AM)),
		C('-', "", List.of());
		
		public final char symbol;
		public final String notation;
		public final List<Session> sessions;
		Cluster(char symbol, String notation, List<Session> sessions) {
			this.symbol = symbol;
			this.notation = notation;
			this.sessions = sessions;
		}
		
		public boolean hasSession(Session session) {
			if(session.getClinic() == Clinic.GENERIC) {
				return sessions.contains(session);
			} else {
				var day = session.getDayOfWeek();
				var period = session.getPeriod();
				for(Session s : sessions) {
					if(s.getDayOfWeek() == day && s.getPeriod().inRange(period.getStartTime(), period.getEndTime())) {
						return true;
					}
				}
				return false;
			}
		}
		
		public static Cluster toCluster(Session s) {
			if(s.getDayOfWeek() == DayOfWeek.FRIDAY) {
				// TODO: calculate which cluster is up...
				return null;
			} else if(C_12.hasSession(s)) {
				return C_12;
			} else if(C_34.hasSession(s)) {
				return C_34;
			} else {
				throw new RuntimeException("Unrecognized session: " + s);
			}
		}
		
		public static Cluster toCluster(String s) {
			for(Cluster c : Cluster.values()) {
				if(c.notation.equals(s)) {
					return c;
				}
			}
			
			System.err.println("Unable to find cluster: " + s);
			return null;
		}
	}
	
	public static enum PerioGroup {
		A,
		B,
		C;
	}
	
	public static enum StudentProgram {
		DOMESTIC,
		INTERNATIONAL;
	}
	
	public static enum UpperLower {
		UPPER,
		LOWER,
		NEITHER,
		UNKNOWN;
		
		public char toChar() {
			switch(this) {
				case UPPER: return 'U';
				case LOWER: return 'L';
				default: return '?';
			}
		}
	}
	
	@Override
	public int compareTo(Student o) {
		return id.compareTo(o.id);
	}
}
