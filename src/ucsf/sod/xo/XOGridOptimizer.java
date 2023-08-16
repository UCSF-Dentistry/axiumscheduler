package ucsf.sod.xo;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import ucsf.sod.objects.StudentYear;
import ucsf.sod.util.SODDateUtils;
import ucsf.sod.util.SODExcelFactory;
import ucsf.sod.util.SODUtil;
import ucsf.sod.xo.calendar.AcademicCalendar;
import ucsf.sod.xo.objects.GroupPractice;
import ucsf.sod.xo.objects.PerioSession;
import ucsf.sod.xo.objects.Rotation;
import ucsf.sod.xo.objects.Student;
import ucsf.sod.xo.objects.Student.StudentProgram;

public class XOGridOptimizer {

	private static String xoGridFileName;

	public static void main(String[] args) throws Exception {
		
		xoGridFileName = args[0];
		String xoGridEvalDirectory = args[1];
		
		if(AcademicCalendar.CURRENT_YEAR != AcademicCalendar.AY2023_2024) {
			System.err.println("Future flag not set; setting");
			AcademicCalendar.CURRENT_YEAR = AcademicCalendar.AY2023_2024;
		}
		
		XOGridReader reader = XOGridReader2023.of(SODUtil.openWorkbook(xoGridFileName));
		System.out.println("X-O Grid Loaded");
		
		Function<Student, XOGridReader> getXOGrid = s -> {			
			return reader;
		};
		
		XOGridOptimizer opt = new XOGridOptimizer(getXOGrid, LocalDate.of(2023, Month.JUNE, 12), LocalDate.of(2024, Month.JUNE, 14));
		
		File outputFile = SODUtil.getTimestampedFile(xoGridEvalDirectory, "rotationOrder", "xlsx");
		opt.evalXOGrid(outputFile);
		Desktop.getDesktop().open(outputFile);
		//opt.start();
	}

	private class Swap implements Comparable<Swap> {
		final Rotation r;
		final LocalDate src;
		final LocalDate dst;
		final List<Student> srcStudent;
		final List<Student> dstStudent;
		final List<String> history;
		
		Swap(Rotation r, LocalDate src, LocalDate dst, List<Student> srcStudent, List<Student> dstStudent, List<String> history) {
			this.r = r;
			this.src = src;
			this.dst = dst;
			this.srcStudent = srcStudent;
			this.dstStudent = dstStudent;
			
			for(Student s : srcStudent) {
				if(getRotation(s, dst) != Rotation.CLINIC) {
					throw new IllegalArgumentException(s.id + " currently is assigned to " + getRotation(s, dst) + " on " + dst);
				}
			}
			
			for(Student s : dstStudent) {
				if(getRotation(s, src) != Rotation.CLINIC) {
					throw new IllegalArgumentException(s.id + " currently is assigned to " + getRotation(s, src) + " on " + src);
				}
			}
			
			this.history = history;
		}
		
		@Override
		public String toString() {
			return r + "|" + src + "|" + dst + "|" + srcStudent + "|" + dstStudent;
		}

		@Override
		public int compareTo(Swap o) {
			int result = src.compareTo(o.src);
			if(result == 0) {
				return dst.compareTo(o.dst);
			}
			return result;
		}
	}
	
	private final LocalDate START;
	private final LocalDate END;
	
	private final Function<Student, XOGridReader> getXOGrid;
	private BufferedReader cmd = new BufferedReader(new InputStreamReader(System.in));
	private String line;
	private List<Swap> swaps = new ArrayList<Swap>();
	private Map<Student, Map<LocalDate, Rotation>> swapOverlay = new HashMap<Student, Map<LocalDate, Rotation>>();
	private Map<LocalDate, Map<GroupPractice, List<Student>>> studentsUnavailable;

	public XOGridOptimizer(Function<Student, XOGridReader> f, LocalDate start, LocalDate end) {
		getXOGrid = f;
		this.START = start;
		this.END = end;
	}
	
	private void identifyImpacted(GroupPractice p, boolean showAll) {
		int totalImpact = 0;
		LocalDate date = START;
		LocalDate endDate = END;
		for(; date.isBefore(endDate); date = date.plusDays(7)) {
			Map<GroupPractice, List<Student>> impact = studentsUnavailable.get(date);
			List<Student> l = impact.getOrDefault(p, List.of());
			if(l.size() <= 10) {
				if(showAll) {
					System.out.println(date + "\tN ["+l.size()+"]");
				}
			} else {
				System.out.println(date + "\tY -- " + String.format("A[%d], B[%d], C[%d], D[%d], E[%d], F[%d]",
					impact.get(GroupPractice.A).size(),
					impact.get(GroupPractice.B).size(),
					impact.get(GroupPractice.C).size(),
					impact.get(GroupPractice.D).size(),
					impact.get(GroupPractice.E).size(),
					impact.get(GroupPractice.F).size()
					)
				);
				totalImpact++;
			}
		}
		System.out.println("Total impact is " + totalImpact);
	}
	
	private void refreshUnavailability() {
		LocalDate date = START;
		LocalDate endDate= END;
		studentsUnavailable = new HashMap<LocalDate, Map<GroupPractice, List<Student>>>();
		for(; date.isBefore(endDate); date = date.plusDays(7)) {
			List<Student> onRotation = new ArrayList<Student>();
			for(Rotation r : List.of(Rotation.ZSFGH, Rotation.D1201, Rotation.ORTHO, Rotation.OM, Rotation.PROS, Rotation.EXT, Rotation.ENDO, Rotation.PGA, Rotation.HD, Rotation.XRAY, Rotation.PEDS, Rotation.B)) {
				onRotation.addAll(r.getStudents(date));
			}
			studentsUnavailable.put(date, onRotation.stream().collect(Collectors.groupingBy(s -> s.practice)));
		}
		printInfo("Student availability refreshed");
	}
	
	public void start() throws IOException {
		refreshUnavailability();
		boolean loop = true;
		while(loop) {
			String line = prompt(new StringBuilder()
				.append("What do you want to do?").append('\n')
				.append("\t1 -- do swap").append('\n')
				.append("\t2 -- view impact for a date").append('\n')
				.append("\t3 -- identify impact for a practice").append('\n')
				.append("\t4 -- count the number of swaps").append('\n')
				.append("\t5 -- view the last swap").append('\n')
				.append("\t6 -- view the schedule for a student").append('\n')
				.append("\t66 -- students in clinic").append('\n')
				.append("\t7 -- view student supply for a practice for a given week").append('\n')
				.append("\t8 -- view student supply for a practice for AY").append('\n')
				.append("\t47 -- view D3 perio schedule for student").append('\n')
				.append("\t48 -- view D3 perio grouping").append('\n')
				.append("\t49 -- view D3 perio student availability").append('\n')
				.append("\t0 -- dump the swap info and exit").append('\n')
				.append("Option: ")
				.toString()
			);
			switch(line) {
			case "1": {
				doSwap();
				break;
			}
			case "2": {
				LocalDate date = readDate();
				if(date != null) {
					printImpact(date);
				}
				break;
			}
			case "3": {
				GroupPractice practice = readGroupPractice();
				if(practice != null) {
					identifyImpacted(practice, true);
				}
				break;
			}
			case "4": {
				System.out.println("Swaps generated: " + swaps.size());
				break;
			}
			case "5": {
				System.out.println("1");
				for(String _s : swaps.get(swaps.size()-1).history) {
					System.out.println(_s);
				}
				System.out.println("Y");
				break;
			}
			case "6": {
				Student s = readStudent();
				if(s != null) {
					System.out.println("Proposed rotation schedule for " + s.id + "|" + s.getPartner().id);
					LocalDate date = START;
					LocalDate endDate= END;
					for(; date.isBefore(endDate); date = date.plusDays(7)) {
						System.out.println(date + "\t" + getRotation(s, date));
					}
				}
				break;
			}
			case "66": {
				GroupPractice practice = readGroupPractice();
				LocalDate date = readDate();
				if(date == null)
					break;

				var asdf = Rotation.CLINIC.getStudents(date).stream().filter(_s -> _s.practice == practice).collect(Collectors.toList());
				System.out.println(asdf);
				break;
			}
			case "7": {
				LocalDate date = readDate();
				if(date == null) {
					break;
				} else if(date.getDayOfWeek() != DayOfWeek.MONDAY) {
					date = SODDateUtils.floorToLastMonday(date);
				}
				
				GroupPractice practice = readGroupPractice();
				if(practice == null) {
					break;
				}
				
				var cluster = Rotation.CLINIC.getStudentsByPractice(practice, date).stream().collect(Collectors.groupingBy(s -> s.cluster));
				System.out.println(cluster);
				//printStudentSupply(date, cluster);
				break;
			}
			case "8": {
				GroupPractice practice = readGroupPractice();
				if(practice == null) {
					break;
				}
				
				LocalDate date = START;
				LocalDate endDate = END;
				for(; date.isBefore(endDate); date = date.plusDays(7)) {
					var cluster = Rotation.CLINIC.getStudentsByPractice(practice, date).stream().collect(Collectors.groupingBy(s -> s.cluster));
					//printStudentSupply(date, cluster);
				}
				break;
			}
			case "47": {
				Student s = readStudent();
				if(!s.isD3()) {
					System.err.println("Student does not have D3 perio");
				} else {
					LocalDate date = SODDateUtils.ceilingToDayOfWeek(START, s.d3perio.getDayOfWeek());
					LocalDate endDate = END;
					XOGridReader reader = getXOGrid(s);
					for(; date.isBefore(endDate); date = date.plusDays(7)) {
						System.out.println(date + "\t" + reader.inD3Perio(s, date, s.d3perio.period) + "\t" + reader.getRotationByStudent(s, date, null));
					}
				}
				break;
			}
			case "48": {
				listD3PerioAssignment();
				break;
			}
			case "49": {
				
				Map<PerioSession, Set<Student>> perio = new HashMap<PerioSession, Set<Student>>();
				for(GroupPractice p : XOGridUtils.PRACTICES) {
					p.getStudentsStream(Student::isD3).forEach(s -> {
						PerioSession session = s.d3perio;
						Set<Student> _s = perio.get(session);
						if(_s == null) {
							perio.put(session, _s = new HashSet<Student>());
						}
						_s.add(s);
					});
				}

				for(PerioSession s : new TreeSet<PerioSession>(perio.keySet())) {
					System.out.println(s);
					System.out.println();
					
					LocalDate date = SODDateUtils.ceilingToDayOfWeek(START, s.getDayOfWeek());
					LocalDate endDate = END;
					for(; date.isBefore(endDate); date = date.plusDays(7)) {
						List<Student> l = new ArrayList<Student>();
						for(Student _s : perio.get(s)) {
							if(getRotation(_s, date) == Rotation.CLINIC) {
								l.add(_s);
							}
						}
						
						System.out.println(date + "\t" + l.size() + "\t" + l);
					}
				}
				break;
				
			}
			case "0": {

				System.out.println("======================================================");
				for(Swap s : swaps) {
					System.out.println("1");
					for(String _s : s.history) {
						System.out.println(_s);
					}
					System.out.println("Y");
				}
				System.out.println("======================================================");
				Map<Rotation, List<Swap>> sorted = swaps.stream().collect(Collectors.groupingBy(s -> s.r));
				for(Rotation r : sorted.keySet()) {
					List<Swap> _s = sorted.get(r);
					_s.sort((s1, s2) -> s1.compareTo(s2));
					System.out.println("Rotation " + r + " (" + _s.size() + ")");
					System.out.println();
					for(Swap s : _s) {
						if(s.src.isBefore(s.dst)) {
							System.out.println("\t" + s.srcStudent + " " + s.src + " <--> " + s.dst + " " + s.dstStudent);
						} else {
							System.out.println("\t" + s.dstStudent + " " + s.dst + " <--> " + s.src + " " + s.srcStudent);
						}
					}
					System.out.println();
				}

				//evalXOGrid("D:\\Cygwin\\home\\mle\\2021_Q2_Spring\\PCC139\\rotationOrder-20210226.xlsx");
				evalXOGrid(SODUtil.getTimestampedFile("D:\\Cygwin\\home\\mle\\2022_Q1_Winter\\PCC139", "rotationOrder", "xlsx"));

				System.out.println("======================================================");

				for(Swap s : swaps) {
					if(s.src.isBefore(s.dst)) {
						System.out.println(s.r + "\t" + s.srcStudent + " " + s.src + " <--> " + s.dst + " " + s.dstStudent);
					} else {
						System.out.println(s.r + "\t" + s.dstStudent + " " + s.dst + " <--> " + s.src + " " + s.srcStudent);
					}
				}

				System.out.println("======================================================");
				printInfo("Exiting...");
				loop = false;
				break;
			}
			default:
				printInfo("Please enter a command.");
			}
		}
	}
	
	private void printImpact(LocalDate date) {
		Map<GroupPractice, List<Student>> impact = studentsUnavailable.get(date);
		printInfo("Impact for "+date+" is the following: "+String.format("A[%d], B[%d], C[%d], D[%d], E[%d], F[%d]",
			impact.get(GroupPractice.A).size(),
			impact.get(GroupPractice.B).size(),
			impact.get(GroupPractice.C).size(),
			impact.get(GroupPractice.D).size(),
			impact.get(GroupPractice.E).size(),
			impact.get(GroupPractice.F).size()
			)
		);
	}
	
	public boolean confirmSwap(List<Swap> l) throws IOException {
		printInfo("If swap confirmed, this is what the schedule will be for each student: ");

		Map<Student, Map<LocalDate, String>> summary = new HashMap<Student, Map<LocalDate, String>>();
		Student[] order = new Student[4];
		int orderIndex = 0;
		Set<LocalDate> dates = new TreeSet<LocalDate>();
		
		Iterator<Swap> iter = l.iterator();
		Swap swp = iter.next();
		{
			{
				boolean src = true;
				for(List<Student> _l : List.of(swp.srcStudent, swp.dstStudent)) {
					LocalDate start = (src ? swp.dst : swp.src).minusWeeks(3);
					LocalDate end = start.plusWeeks(7);
					for(Student s : _l) {
						Map<LocalDate, String> rotationMap = new HashMap<LocalDate, String>();
						for(LocalDate date = start; end.isAfter(date); date = date.plusWeeks(1)) {
							rotationMap.put(date, getRotation(s, date).toString());
							dates.add(date);
						}
	
						summary.put(s, rotationMap);
						order[orderIndex++] = s;
					}
					if(orderIndex % 2 == 1) {
						orderIndex++;
					}
					src = false;
				}
			}
	
			do {
				boolean src = true;
				for(List<Student> _l : List.of(swp.srcStudent, swp.dstStudent)) {
					for(Student s : _l) {
						Map<LocalDate, String> rotationMap = summary.get(s);
						rotationMap.put((src ? swp.dst : swp.src), "[" + swp.r + "]");
	
						if(rotationMap.containsKey((src ? swp.src : swp.dst))) {
							rotationMap.put((src ? swp.src : swp.dst), "<" + Rotation.CLINIC + ">");
						}
					}
					src = false;
				}			
				
				swp = iter.hasNext() ? iter.next() : null;
			} while(swp != null);
		}
		
		String[][] display = new String[dates.size()+1][5];
		{
			String[] _row = display[0];
			_row[0] = "";
			orderIndex = 1;
			for(Student s : order) {
				_row[orderIndex++] = (s == null ? "" : (s.id + "[" + s.practice +  "]"));
			}

			int row = 1;
			for(LocalDate date : dates) {
				_row = display[row];
				_row[0] = date.toString();
				orderIndex = 1;
				for(Student s : order) {
					_row[orderIndex] = s == null ? "" : summary.get(s).getOrDefault(date, "");
					orderIndex++;
				}
				row++;
			}
		}
		
		for(String[] row : display) {
			for(String c : row) {
				System.out.print(c);
				System.out.print('\t');
			}
			System.out.println();
		}
		
		while(true) {
			line = prompt("Do you want to follow through?");
			if("Y".equals(line.toUpperCase())) {
				l.forEach(this::executeSwap);
				refreshUnavailability();
				return true;
			} else {
				return false;
			}
		}
	}
	
	public void executeSwap(Swap s) {
		printInfo("Swapping");
		Rotation r = s.r;
		r.removeStudents(s.src, s.srcStudent);
		r.removeStudents(s.dst, s.dstStudent);
		r.registerStudents(s.src, s.dstStudent);
		r.registerStudents(s.dst, s.srcStudent);
		swaps.add(s);
		
		for(Student _s : s.srcStudent) {
			Map<LocalDate, Rotation> lookup = swapOverlay.get(_s);
			if(lookup == null) {
				swapOverlay.put(_s, lookup = new HashMap<LocalDate, Rotation>());
			}
			lookup.put(s.src, Rotation.CLINIC);
			lookup.put(s.dst, s.r);
		}
		
		for(Student _s : s.dstStudent) {
			Map<LocalDate, Rotation> lookup = swapOverlay.get(_s);
			if(lookup == null) {
				swapOverlay.put(_s, lookup = new HashMap<LocalDate, Rotation>());
			}
			lookup.put(s.dst, Rotation.CLINIC);
			lookup.put(s.src, s.r);
		}
	}
	
	public void doSwap() throws IOException {
		
		GroupPractice impactedPractice = readGroupPractice();
		if(impactedPractice == null) {
			printError("Cancelling");
			return;
		} else {
			printInfo("Selected " + impactedPractice);
		}

		identifyImpacted(impactedPractice, false);

		LocalDate date1 = readDate();
		if(date1 == null) {
			printError("Cancelling");
			return;
		} else {
			printInfo("Selected " + date1);
		}
		
		Map<Rotation, List<Student>> rotationStudents = studentsUnavailable.getOrDefault(date1, Map.of()).getOrDefault(impactedPractice, List.of()).stream().collect(Collectors.groupingBy(s -> getRotation(s, date1)));
		if(rotationStudents.size() == 0) {
			printInfo("There are no students on rotation for the practice");
			return;
		}

		Rotation r1 = null;
		while(true) {
	 		printInfo("The following students are on rotation:");
			for(Rotation r : rotationStudents.keySet()) {
				if(r == Rotation.B)
					continue;
				else if(r == Rotation.CLINIC) {
					System.err.println("Clinic detected in rotation");
					return;
				}
				
				printInfo("\t" + r + "\t" + rotationStudents.get(r));
			}
			r1 = readRotation();
			if(r1 == null || rotationStudents.containsKey(r1)) {
				break;
			} else {
				printError("Students from group practice " + impactedPractice + " are not assigned to " + r1 + " on " + date1);
			}
		}
		if(r1 == null) {
			printError("Cancelling");
			return;
		} else {
			printInfo("Selected " + r1);
		}
		
		Student s1 = null;
		Set<Student> l = r1.getStudents(date1).stream().filter(s -> s.practice == impactedPractice).collect(Collectors.toSet());
		while(true) {
			printInfo("The following students are on rotation: ");
			for(Student s : l) {
				printInfo("\t" + s.id + " (" + s.practice + ")");
			}
		
			line = prompt("Which student do you want to swap?");
			if(line.length() == 0) {
				break;
			}
			
			s1 = Student.getStudent(line.toUpperCase());
			if(s1 != null) {
				if(l.contains(s1)) {
					break;
				} else {
					printError("Student not on rotation");
				}
			} else {
				printError("Unable to find student");
			}
		}
		if(s1 == null) {
			System.err.println("Cancelling");
			return;
		} else {
			printInfo("Selected " + s1.id);
		}

		// Select the practice based on impact
		printImpact(date1);
		GroupPractice targetPractice = readGroupPractice();
		if(targetPractice == null) {
			printError("Cancelling");
			return;
		} else {
			printInfo("Selected " + targetPractice);
		}
		
		while(true) {
			findOptions(r1, date1, s1, targetPractice);
			
			LocalDate date2 = readDate();
			if(date2 == null) {
				printError("Re-evaluating group practice");
				printImpact(date1);
				targetPractice = readGroupPractice();
				if(targetPractice == null) {
					printError("Cancelling");
					return;
				} else {
					continue;
				}
			}
			
			{
				GroupPractice selected = targetPractice;
				l = r1.getStudents(date2).stream().filter(s -> s.practice == selected).collect(Collectors.toSet());
				printInfo("The following students are on rotation: ");
				for(Student s : l) {
					printInfo("\t" + s.id + " (" + s.practice + ")");
				}
			}
			
			Student s2 = null;
			while(true) {
				line = prompt("Which student do you want to swap?");
				if(line.length() == 0) {
					break;
				}
				
				s2 = Student.getStudent(line.toUpperCase());
				if(s2 != null) {
					if(l.contains(s2)) {
						break;
					} else {
						printError("Student not in the set");
					}
				} else {
					printError("Unable to find student");
				}
			}
			if(s2 == null) {
				System.err.println("Cancelling");
				continue;
			} else {
				printInfo("Selected " + s2.id);
			}
			
			if(validSwap(r1, s1, s2, date1, date2)) {
				List<Swap> swapList = new ArrayList<Swap>();

				{
					List<Student> srcStudent;
					if(getRotation(s1.getPartner(), date1) == r1) {
						srcStudent = List.of(s1, s1.getPartner());
					} else {
						srcStudent = List.of(s1);
					}
					
					List<Student> dstStudent;
					if(getRotation(s2.getPartner(), date2) == r1) {
						dstStudent = List.of(s2, s2.getPartner());
					} else {
						dstStudent = List.of(s2);
					}
	
					swapList.add(new Swap(
						r1, 
						date1, 
						date2, 
						srcStudent,
						dstStudent, 
						List.of(impactedPractice.toString(), date1.toString(), r1.toString(), s1.id, targetPractice.toString(), date2.toString(), s2.id)
					));
				}
				
				// Check that the second week can be swapped
				if(r1 == Rotation.ZSFGH || (r1 == Rotation.D1201 && s1.year == StudentYear.FOURTH_YEAR) || r1 == Rotation.PEDS) {
					LocalDate date1B = date1.plusWeeks(1);
					Rotation r1_srcB = getRotation(s1, date1B);
					if(r1_srcB != r1) {
						System.err.println("Found " + r1_srcB + " on " + date1B + ", instead of " + r1);
						date1B = date1.minusWeeks(1);
						r1_srcB = getRotation(s1, date1B);
						System.err.println("Found " + r1_srcB + " on " + date1B + ", seeking " + r1);
					}
					
					LocalDate date2B = date2.plusWeeks(1);
					Rotation r1_dstB = getRotation(s2, date2B);
					if(r1_dstB != r1) {
						System.err.println("Found " + r1_dstB + " on " + date2B + ", instead of " + r1);
						date2B = date2.minusWeeks(1);
						r1_dstB = getRotation(s2, date2B);
						System.err.println("Found " + r1_dstB + " on " + date2B + ", seeking " + r1);
					}
					
					System.err.println("Validating the following: " + r1 + "\t" + s1.id + "\t" + s2.id + "\t" + date1B + "\t" + date2B);
					if(validSwap(r1, s1, s2, date1B, date2B)) {
						
						List<Student> srcStudent;
						if(getRotation(s1.getPartner(), date1B) == r1) {
							srcStudent = List.of(s1, s1.getPartner());
						} else {
							srcStudent = List.of(s1);
						}
						
						List<Student> dstStudent;
						if(getRotation(s2.getPartner(), date2B) == r1) {
							dstStudent = List.of(s2, s2.getPartner());
						} else {
							dstStudent = List.of(s2);
						}

						swapList.add(new Swap(
							r1, 
							date1B,
							date2B, 
							srcStudent, 
							dstStudent, 
							List.of(impactedPractice.toString(), date1B.toString(), r1.toString(), s1.id, targetPractice.toString(), date2B.toString(), s2.id)
						));
					} else {
						swapList.clear();
					}
				}
				
				if(!swapList.isEmpty()) {
					printInfo("Able to swap " + s1.id + " and " + s2.id);
					boolean result = confirmSwap(swapList);
					if(result) {
						break;
					}
				}
			}
		}
	}
	
	public boolean validSwap(Rotation r, Student s1, Student s2, LocalDate date1, LocalDate date2) {
		
		Rotation r1_dst = getRotation(s1, date2);
		Rotation r2_dst = getRotation(s2, date1);
		if(r1_dst != Rotation.CLINIC) {
			printError("Cannot swap as " + s1.id + " is assigned to " + r1_dst + " on " + date2);
			return false;
		} else if(r2_dst != Rotation.CLINIC) {
			printError("Cannot swap as " + s2.id + " is assigned to " + r2_dst + " on " + date1);
			return false;
		}
		
		return true;
	}
	
	public void findOptions(Rotation r, LocalDate src, Student s, GroupPractice p2) {
		printInfo("These are the options\n");
		Map<LocalDate, Set<Student>> alternatives = r.getStudentsByPractice(p2);
		if(r == Rotation.PEDS) {
			LocalDate cutoff = LocalDate.of(END.getYear(), Month.JANUARY, 1);
			if(s.year == StudentYear.FOURTH_YEAR) {
				for(LocalDate d : alternatives.keySet().stream().filter(d -> d.isAfter(cutoff)).collect(Collectors.toList())) {
					alternatives.remove(d);
				}
			} else {
				for(LocalDate d : alternatives.keySet().stream().filter(d -> d.isBefore(cutoff)).collect(Collectors.toList())) {
					alternatives.remove(d);
				}
			}
		} else if(r == Rotation.D1201) {
			// Do something about if D3, only see D3 options
		}
		
		for(LocalDate d : new TreeSet<LocalDate>(alternatives.keySet())) {
			Map<GroupPractice, List<Student>> countMap = studentsUnavailable.get(d);
			Rotation r1 = getRotation(s, d);

			Set<Student> processed = new HashSet<Student>(alternatives.get(d));
			for(Student candidate : alternatives.get(d)) {
				List<Student> cList;
				if(!processed.contains(candidate)) {
					continue;
				} else {
					cList = new ArrayList<Student>();
					cList.add(candidate);
					processed.remove(candidate);
					
					Student cPartner = candidate.getPartner();
					if(processed.contains(cPartner)) {
						cList.add(cPartner);
						processed.remove(cPartner);
					}
				}
				
				Rotation r2 = getRotation(candidate, src);
				boolean potential = r1 == Rotation.CLINIC && r2 == Rotation.CLINIC;
				
				int impactSize = countMap.getOrDefault(s.practice, List.of()).size();
				int targetSize = countMap.getOrDefault(p2, List.of()).size();
				boolean feasible = (impactSize < 10 && targetSize >= 10) || (impactSize < 10 && targetSize < 10);
				
				printInfo(d + "\t" + s.practice + " ["+impactSize+"]\t" + r1 + "\t" + p2 + "["+targetSize+"]\t" + cList + "\t" + r2 + "\t" + (potential && feasible ? "O" : "X"));
			}
		}
	}
	
	private void listD3PerioAssignment() {
		Map<PerioSession, Set<Student>> perio = new HashMap<PerioSession, Set<Student>>();
		for(GroupPractice p : XOGridUtils.ALL_PRACTICES) {
			p.getStudentsStream(Student::isD3).forEach(s -> {
				PerioSession session = s.d3perio;
				Set<Student> _s = perio.get(session);
				if(_s == null) {
					perio.put(session, _s = new HashSet<Student>());
				}
				_s.add(s);
			});
		}
		
		for(PerioSession s : new TreeSet<PerioSession>(perio.keySet())) {
			var asdf = perio.get(s).stream().collect(
				Collectors.groupingBy(
					_s -> _s.practice,
					Collectors.counting()
			));
			System.out.println(s + asdf.toString());
			for(Student _s : perio.get(s)) {
				System.out.println(s + "\t" + _s.id + "\t" + _s.first + "\t" + _s.last + "\t" + _s.practice);
			}
		}
	}
	
	/*
	 * Reader functions
	 * 
	 */
	private Rotation readRotation() throws IOException {
		Rotation r = null;
		while(true) {
			line = prompt("Which rotation?");
			if(line.length() == 0) {
				break;
			}
			
			try {
				r = Rotation.valueOf(line.toUpperCase());
				break;
			} catch (IllegalArgumentException ex) {
				printError("Invalid rotation");
			}
		}
		
		return r;
	}
	
	private GroupPractice readGroupPractice() throws IOException {
		GroupPractice practice = null;
		while(true) {
			line = prompt("Which group practice?");
			if(line.length() == 0) {
				break;
			}
			
			try {
				practice = GroupPractice.valueOf(line.toUpperCase());
				break;
			} catch (IllegalArgumentException ex) {
				printError("Invalid group practice");
			}
		}

		return practice;
	}
	
	private LocalDate readDate() throws IOException {
		LocalDate date1 = null;
		while(true) {
			line = prompt("From which date?");
			if(line.length() == 0) {
				break;
			} else if(line.contains(".")) {
				line = line.replace('.', '-');
			}
			
			try {
				date1 = SODDateUtils.floorToLastMonday(LocalDate.parse(line));
				break;
			} catch (DateTimeParseException ex) {
				printError("Invalid date");
			}
		}

		return date1;
	}
	
	private Student readStudent() throws IOException {
		Student s1 = null;
		while(true) {
			line = prompt("Which student do you want to select?");
			if(line.length() == 0) {
				break;
			}
			
			s1 = Student.getStudent(line.toUpperCase());
			if(s1 != null) {
				break;
			} else {
				printError("Unable to find student");
			}
		}
		
		return s1;
	}
	
	/*
	 * Lookup functions
	 */
	
	private Rotation getRotation(Student s, LocalDate d) {
		if(s == null || s == Student.PLACEHOLDER) {
			return Rotation.UNKNOWN;
		}
		
		Map<LocalDate, Rotation> lookup = swapOverlay.get(s);
		if(lookup != null) {
			Rotation r = lookup.get(d);
			if(r != null) {
				return r;
			}
		}
		
		return getXOGrid(s).getRotationByStudent(s, d, null);
	}
	
	private XOGridReader getXOGrid(Student s) {
		return getXOGrid.apply(s);
	}
	
	/*
	 * Printing functions
	 */
	
	private void printInfo(String s) {
		System.out.println(s);
	}
	
	private void printError(String s) {
		System.err.println(s);
	}
	
	private String prompt(String s) throws IOException {
		System.out.print(s);
		System.out.print(" ");
		return cmd.readLine();
	}

	/*
	 * Validators
	 */

	public void missingRotationStudents() {

		// Validate ID4 assigned to all rotations
		for(Rotation r : List.of(Rotation.ZSFGH, Rotation.D1201, Rotation.PEDS, Rotation.PROS, Rotation.OM)) {
			Set<Student> unassigned = Student.getStudentsSorted(s -> s.isID4());
			Map<LocalDate, Set<Student>> schedule = r.getRotationSchedule();
			for(LocalDate date : new TreeSet<LocalDate>(schedule.keySet())) {
				Set<Student> l = schedule.get(date);
				unassigned.removeAll(l);
				//System.out.println(date + "\t" + XOGridUtils.wrapStudentList(l));
			}
			System.err.println(r + "\tUnassigned: " + unassigned);
		}

		// Validate ID3 assigned to all rotations
		for(Rotation r : List.of(Rotation.PGA, Rotation.ZSFGH, Rotation.ORTHO, Rotation.PEDS)) {
			Set<Student> unassigned = Student.getStudentsSorted(s -> s.isID3());
			Map<LocalDate, Set<Student>> schedule = r.getRotationSchedule();
			for(LocalDate date : new TreeSet<LocalDate>(schedule.keySet())) {
				Set<Student> l = schedule.get(date);
				unassigned.removeAll(l);
				//System.out.println(date + "\t" + XOGridUtils.wrapStudentList(l));
			}
			System.err.println(r + "\tUnassigned: " + unassigned);
		}

		// Validate D4 assigned to all rotations
		for(Rotation r : List.of(Rotation.PROS, Rotation.D1201, Rotation.ZSFGH, Rotation.EXT)) {
			Set<Student> unassigned = Student.getStudentsSorted(s -> s.isD4());
			Map<LocalDate, Set<Student>> schedule = r.getRotationSchedule();
			for(LocalDate date : new TreeSet<LocalDate>(schedule.keySet())) {
				Set<Student> l = schedule.get(date);
				unassigned.removeAll(l);
				//System.out.println(date + "\t" + XOGridUtils.wrapStudentList(l));
			}
			System.err.println(r + "\tUnassigned: " + unassigned);
		}
		
		// Validate D3 assigned to all rotations
		for(Rotation r : List.of(Rotation.XRAY, Rotation.HD, Rotation.ORTHO, Rotation.OM, Rotation.D1201, Rotation.PGA, Rotation.ENDO, Rotation.PEDS)) {
			Set<Student> unassigned = Student.getStudentsSorted(s -> s.isD3());
			Map<LocalDate, Set<Student>> schedule = r.getRotationSchedule();
			for(LocalDate date : new TreeSet<LocalDate>(schedule.keySet())) {
				Set<Student> l = schedule.get(date);
				unassigned.removeAll(l);
				//System.out.println(date + "\t" + XOGridUtils.wrapStudentList(l));
			}
			System.err.println(r + "\tUnassigned: " + unassigned);
		}
	}
	
	public void evalXOGrid(File outputFile) throws IOException {

		/*
		// View clinic manpower
		{
			Map<LocalDate, List<Student>> schedule = Rotation.CLINIC.getRotationSchedule();
			StringBuilder builder = new StringBuilder();
			for(LocalDate date : new TreeSet<LocalDate>(schedule.keySet())) {
				Map<GroupPractice, List<Student>> distribution = schedule.get(date).stream().collect(Collectors.groupingBy(s -> s.practice));
				builder.append(date);
				for(GroupPractice p : List.of(GroupPractice.F)) {
					builder.append('\t').append(distribution.get(p).size());
				}
				builder.append('\n');
			}
			System.out.println(builder.toString());
		}
		*/
		{
			LocalDate date = START;
			LocalDate endDate= END;
			Pair<StudentProgram, StudentYear> d4 = Pair.of(StudentProgram.DOMESTIC, StudentYear.FOURTH_YEAR);
			Pair<StudentProgram, StudentYear> id4 = Pair.of(StudentProgram.INTERNATIONAL, StudentYear.FOURTH_YEAR);
			Pair<StudentProgram, StudentYear> d3 = Pair.of(StudentProgram.DOMESTIC, StudentYear.THIRD_YEAR);
			Pair<StudentProgram, StudentYear> id3 = Pair.of(StudentProgram.INTERNATIONAL, StudentYear.THIRD_YEAR);
			List<Pair<StudentProgram, StudentYear>> types = List.of(d4, id4, d3, id3);
			SODExcelFactory factory = new SODExcelFactory();
			// Write Header
			{
				List<String> builder = new ArrayList<String>();
				builder.add("");
				for(int i = 0; i < 6; i++) {
					builder.add("");
				}
				for(Rotation r : List.of(Rotation.ZSFGH, Rotation.D1201, Rotation.ORTHO, Rotation.OM, Rotation.PROS, Rotation.EXT, Rotation.ENDO, Rotation.PGA, Rotation.HD, Rotation.XRAY, Rotation.PEDS)) {
					builder.add(r.toString());
					builder.addAll(List.of("", "", "", ""));
				}
				factory.createRow(builder);
				builder.clear();
				builder.add("");
				for(GroupPractice p : XOGridUtils.ALL_PRACTICES) {
					builder.add(p.toString());
				}
				List.of(Rotation.ZSFGH, Rotation.D1201, Rotation.ORTHO, Rotation.OM, Rotation.PROS, Rotation.EXT, Rotation.ENDO, Rotation.PGA, Rotation.HD, Rotation.XRAY, Rotation.PEDS).forEach(r -> {
					builder.add("Count");
					builder.add("D4");
					builder.add("ID4");
					builder.add("D3");
					builder.add("ID3");
				});
				factory.createRow(builder);
			}
			
			// Fill Data
			Map<Rotation, Map<Pair<StudentProgram, StudentYear>, Pair<String, Integer>>> lastWeek = new HashMap<>();
			Map<Rotation, Map<Pair<StudentProgram, StudentYear>, List<Pair<Integer, Integer>>>> mergeRanges = new HashMap<>();
			{
				{
					List<Map.Entry<Rotation, Map<Pair<StudentProgram, StudentYear>, Pair<String, Integer>>>> map = new ArrayList<Map.Entry<Rotation, Map<Pair<StudentProgram, StudentYear>, Pair<String, Integer>>>>();
					for(Rotation r : List.of(Rotation.ZSFGH, Rotation.D1201, Rotation.ORTHO, Rotation.OM, Rotation.PROS, Rotation.EXT, Rotation.ENDO, Rotation.PGA, Rotation.HD, Rotation.XRAY, Rotation.PEDS)) {
						var m = new HashMap<Pair<StudentProgram, StudentYear>, Pair<String, Integer>>();
						m.put(d4, Pair.of(null, null));
						m.put(id4, Pair.of(null, null));
						m.put(d3, Pair.of(null, null));
						m.put(id3, Pair.of(null, null));
						map.add(Map.entry(r, m));
					}
					
					for(Map.Entry<Rotation, Map<Pair<StudentProgram, StudentYear>, Pair<String, Integer>>> e : map) {
						lastWeek.put(e.getKey(), e.getValue());
					}
				}
				{
					List<Map.Entry<Rotation, Map<Pair<StudentProgram, StudentYear>, List<Pair<Integer, Integer>>>>> map = new ArrayList<Map.Entry<Rotation, Map<Pair<StudentProgram, StudentYear>, List<Pair<Integer, Integer>>>>>();
					for(Rotation r : List.of(Rotation.ZSFGH, Rotation.D1201, Rotation.ORTHO, Rotation.OM, Rotation.PROS, Rotation.EXT, Rotation.ENDO, Rotation.PGA, Rotation.HD, Rotation.XRAY, Rotation.PEDS)) {
						map.add(Map.entry(r, Map.of(
							d4, new ArrayList<Pair<Integer, Integer>>(),
							id4, new ArrayList<Pair<Integer, Integer>>(),
							d3, new ArrayList<Pair<Integer, Integer>>(),
							id3, new ArrayList<Pair<Integer, Integer>>()
						)));
					}
					
					for(Map.Entry<Rotation, Map<Pair<StudentProgram, StudentYear>, List<Pair<Integer, Integer>>>> e : map) {
						mergeRanges.put(e.getKey(), e.getValue());
					}
				}
			}
			
			int rowCount = 2;
			for(; date.isBefore(endDate); date = date.plusDays(7)) {
				ArrayList<String> builder = new ArrayList<String>();
				builder.add(date.toString());
				List<Student> onRotation = new ArrayList<Student>();
				for(Rotation r : List.of(Rotation.ZSFGH, Rotation.D1201, Rotation.ORTHO, Rotation.OM, Rotation.PROS, Rotation.EXT, Rotation.ENDO, Rotation.PGA, Rotation.HD, Rotation.XRAY, Rotation.PEDS)) {
					Set<Student> all = r.getStudents(date);
					builder.add(Integer.toString(all.size()));
					Map<Pair<StudentProgram, StudentYear>, Pair<String, Integer>> history = lastWeek.get(r);
					Map<Pair<StudentProgram, StudentYear>, List<Pair<Integer, Integer>>> merge = mergeRanges.get(r);
					Map<Pair<StudentProgram, StudentYear>, List<Student>> l = all.stream().collect(Collectors.groupingBy(s -> Pair.of(s.program, s.year)));
					for(Pair<StudentProgram, StudentYear> p : types) {
						List<Student> _l = l.get(p);
						String entry;
						if(_l == null) {
							builder.add(entry = "");
						} else {
							onRotation.addAll(_l);
							builder.add(entry = _l.stream().map(s -> s.id + (s.program == StudentProgram.DOMESTIC ? " (" + s.practice + ")" : "")).collect(Collectors.joining("\n")));
						}
						
						if(entry.length() == 0) {
							Pair<String, Integer> _p = history.get(p);
							if(_p.getLeft() != null && ((rowCount-1) != _p.getRight())) {
								List<Pair<Integer, Integer>> list = merge.get(p);
								list.add(Pair.of(_p.getRight(), rowCount-1));
							}
							history.put(p, Pair.of(null, null));
						} else {
							Pair<String, Integer> _p = history.get(p);
							if(_p.getLeft() == null) {
								history.put(p, Pair.of(entry, rowCount));
							} else if(!_p.getLeft().equals(entry)) {
								if((rowCount-1) != _p.getRight()) {
									List<Pair<Integer, Integer>> list = merge.get(p);
									list.add(Pair.of(_p.getRight(), rowCount-1));
								}
								history.put(p, Pair.of(entry, rowCount));
							}
						}
					}
				}
				
				Map<GroupPractice, Integer> countMap = onRotation.stream().collect(
					Collectors.groupingBy(
						s -> s.practice,
						Collectors.collectingAndThen(
							Collectors.toList(), 
							l -> l.size()
						)
					)
				);
				for(GroupPractice p : List.of(GroupPractice.F, GroupPractice.E, GroupPractice.D, GroupPractice.C, GroupPractice.B, GroupPractice.A)) {
					List<Student> asdf = Rotation.B.getStudents(date).stream().filter(s -> s.practice == p).collect(Collectors.toList());
					int breakCount = asdf.size();
					Integer i = countMap.get(p);
					String _s = "";
					if(i == null) {
						if(breakCount != 0) {
							_s = Integer.toString(breakCount);
						}
					} else if(i != null) {
						if(breakCount != 0) {
							i += breakCount;
						}
						_s = i.toString();
					}
					builder.add(1, _s);
				}
				
				factory.createRow(builder);
				rowCount++;
			}

			int colCount = 6;
			for(Rotation r : List.of(Rotation.ZSFGH, Rotation.D1201, Rotation.ORTHO, Rotation.OM, Rotation.PROS, Rotation.EXT, Rotation.ENDO, Rotation.PGA, Rotation.HD, Rotation.XRAY, Rotation.PEDS)) {
				Map<Pair<StudentProgram, StudentYear>, List<Pair<Integer, Integer>>> merge = mergeRanges.get(r);
				colCount++;
				for(Pair<StudentProgram, StudentYear> p : types) {
					colCount++;
					for(Pair<Integer, Integer> l : merge.get(p)) {
						factory.mergeCells(l.getLeft(), l.getRight(), colCount, colCount);
					}
				}
			}
			
			
			for(int i = 0; i < 7; i++) {
				factory.autofitColumn(i);
			}
			
			factory.export(outputFile);
		}
	}
	
	public static Map<Student, Integer> countsWithLink(XOGridReader reader) {
		Set<Student> students = Student.getStudentsSorted(s -> s.isD3() || s.isD4());
		Iterator<Student> iter = students.iterator();
		LocalDate start = reader.getFirstDate();
		LocalDate end = reader.getLastDate();
		Map<Student, Integer> countsWithLink = new TreeMap<Student, Integer>();
		while(iter.hasNext()) {
			Student a = iter.next();
			if(countsWithLink.containsKey(a)) {
				continue;
			}
			
			Student c = a.getPartner();

			Student b = a.getPrimaryLink();
			Student d;
			if(b != Student.PLACEHOLDER) {
				d = b.getPartner();
			} else {
				d = c.getPrimaryLink();
			}
			
			Map<LocalDate, Rotation> aSchedule = reader.getAcademicSchedule(a, start, end);
			Set<LocalDate> aClinic = aSchedule.entrySet().stream().filter(e -> e.getValue() == Rotation.CLINIC).collect(Collectors.mapping(Map.Entry::getKey, Collectors.toSet()));
			Set<LocalDate> bClinic = Set.of();
			if(b != Student.PLACEHOLDER) {
				bClinic = reader.getAcademicSchedule(b, start, end).entrySet().stream().filter(e -> e.getValue() == Rotation.CLINIC).collect(Collectors.mapping(Map.Entry::getKey, Collectors.toSet()));
			} else if (d != Student.PLACEHOLDER) {
				bClinic = reader.getAcademicSchedule(d, start, end).entrySet().stream().filter(e -> e.getValue() == Rotation.CLINIC).collect(Collectors.mapping(Map.Entry::getKey, Collectors.toSet()));
			} else {
				System.err.println("Both B and D are placeholders");
			}
			
			Set<LocalDate> common = new TreeSet<LocalDate>(aClinic);
			common.retainAll(bClinic);
			
			for(Student s : List.of(a, b, c, d)) {
				if(s != Student.PLACEHOLDER) {
					countsWithLink.put(s, common.size());
				}
			}
		}
		
		return countsWithLink;
	}
	
	public static void printKeyDates(XOGridReader reader) {
		System.out.println("\nHuddle Dates");
		Map<GroupPractice, List<LocalDate>> huddles = reader.getHuddleDatesAll();
		for(GroupPractice p : huddles.keySet()) {
			System.out.println("\nGroup Practice " + p);
			huddles.get(p).forEach(d -> System.out.println("\t" + d));
		}
		
		System.out.println("\nD3 Lecture");
		reader.getD3LectureDates().forEach(d -> System.out.println("\t" + d));
		
		System.out.println("\nD4 Lecture");
		reader.getD4LectureDates().forEach(d -> System.out.println("\t" + d));
	}
}
