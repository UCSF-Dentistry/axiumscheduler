package ucsf.sod.xo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.DataFormatter;

import ucsf.sod.xo.objects.GroupPractice;
import ucsf.sod.xo.objects.Student;
import ucsf.sod.xo.objects.Student.Cluster;

public class XOGridUtils {

	public static final String EMPTY_EMAIL = "none@example.com";

	@Deprecated
	public static boolean PAST = false;
	
	@Deprecated
	public static boolean FUTURE = false;

	/**
	 * Consists of the Parnassus Group Practices
	 */
	public static final List<GroupPractice> PRACTICES = List.of(GroupPractice.A, GroupPractice.B, GroupPractice.C, GroupPractice.D, GroupPractice.E);
	public static final List<GroupPractice> ALL_PRACTICES = List.of(GroupPractice.A, GroupPractice.B, GroupPractice.C, GroupPractice.D, GroupPractice.E, GroupPractice.F);
	public static final LocalDate STALL_CUTOFF = LocalDate.now().minusMonths(3); 

	public static final LocalTime PREDOC_AM_START_CUTOFF = LocalTime.of(8, 30);
	public static final LocalTime PREDOC_AM_END_CUTOFF = LocalTime.of(11, 30);
	public static final LocalTime PREDOC_PM1_START_CUTOFF = LocalTime.of(13, 00);
	public static final LocalTime PREDOC_PM1_END_CUTOFF = LocalTime.of(15, 00);
	public static final LocalTime PREDOC_PM2_START_CUTOFF = LocalTime.of(15, 00);
	public static final LocalTime PREDOC_PM2_END_CUTOFF = LocalTime.of(17, 00);

	public static final LocalTime PERIO_AM_START_CUTOFF = LocalTime.of(8, 30);
	public static final LocalTime PERIO_AM_END_CUTOFF = LocalTime.of(12, 00);
	public static final LocalTime PERIO_PM_START_CUTOFF = LocalTime.of(13, 30);
	public static final LocalTime PERIO_PM_END_CUTOFF = LocalTime.of(17, 00);
	
	public static final Pattern ROTATION_UPPER_ONLY = Pattern.compile("^.+-\\s*[Uu]$");
	public static final Pattern ROTATION_LOWER_ONLY = Pattern.compile("^.+-\\s*[Ll]$");

	public static final DataFormatter df = new DataFormatter();

	public static class ClinicStatus {

		public static enum OpenMode {
			CLOSED(false, false, false, false),
			OPEN(true, true, true, true),
			D3_ONLY(true, true, false, false),
			D4_ONLY(false, false, true, true),
			D4_LECTURE_SUMMER(true, false, false, true),
			D4_LECTURE(true, false, false, false),
			AM_ONLY(true, false, true, false),
			PM_ONLY(false, true, false, true);
			
			public final boolean d3_am;
			public final boolean d3_pm;
			public final boolean d4_am;
			public final boolean d4_pm;
			
			OpenMode(boolean d3_am, boolean d3_pm, boolean d4_am, boolean d4_pm) {
				this.d3_am = d3_am;
				this.d3_pm = d3_pm;
				this.d4_am = d4_am;
				this.d4_pm = d4_pm;
			}
			
			public static boolean[] openStatus(OpenMode status) {
				return new boolean[] { isOpenAM(status), isOpenPM(status)};
			}

			public static boolean isOpen(OpenMode status) {
				return isOpenAM(status) || isOpenPM(status);
			}
			
			public static boolean isOpenAM(OpenMode status) {
				return status.d3_am || status.d4_am;
			}

			public static boolean isOpenPM(OpenMode status) {
				return status.d3_pm || status.d4_pm;
			}
		}
		
		public static final ClinicStatus TYPICAL = new ClinicStatus(LocalDate.MIN, "Typical", OpenMode.OPEN);
		public static final ClinicStatus CLOSED = new ClinicStatus(LocalDate.MIN, "Closed", OpenMode.CLOSED);
		
		public final LocalDate date;
		public final String name;
		public final OpenMode status;
		
		public ClinicStatus(LocalDate date, String name, OpenMode status) {
			this.date = date;
			this.name = name;
			this.status = status;
		}
	}
	
	public static <V> Map<GroupPractice, V> createGroupPracticeEnumMap() {
		return new EnumMap<GroupPractice, V>(GroupPractice.class);
	}
	
	public static List<String> convertAllToString(List<Object> l) {
		return l.stream().collect(Collectors.mapping(Object::toString, Collectors.toList()));
	}
	
	public static Random RANDOM = new Random(9043006002886673L);
	
	public static void shuffle(List<?> l) {
		Collections.shuffle(l, RANDOM);
	}
	
	public static class RotationCounter<T> {
		private LinkedList<T> list;

		public RotationCounter(List<T> list) {
			this.list = new LinkedList<T>(list);
		}
		
		public List<T> getPriorityList() {
			return Collections.unmodifiableList(list);
		}
		
		public void advancePriorityList(T selected) {
			list.remove(selected);
			list.addLast(selected);
		}
		
		public static Map<Cluster, RotationCounter<Character>> createClusterRotationCounter() {
			return Map.of(
				Cluster.C_12, new RotationCounter<Character>(List.of('1', '2', '3', '4', '5')),
				Cluster.C_34, new RotationCounter<Character>(List.of('1', '2', '3', '4'))
			);
		}
	}
	
	/**
	 * Obtain a presentable string of the student's ID, first, and last name
	 * @param s the student whose name to print out
	 * @return if null is passed, an empty string; otherwise, the student's ID, first, and last name
	 */
	public static String getReadableStudentName(Student s) {
		if(s == null) {
			return "";
		} else {
			return s.id + " " + s.first + " " + s.last;
		}
	}
}
