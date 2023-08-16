package ucsf.sod.xo;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import ucsf.sod.objects.DatedSession;
import ucsf.sod.objects.GenericPeriod;
import ucsf.sod.objects.GenericSession;
import ucsf.sod.objects.Period;
import ucsf.sod.util.SODDateUtils;
import ucsf.sod.util.SODExcelFactory;
import ucsf.sod.util.SODUtil;
import ucsf.sod.xo.ChairScheduler.ChairAssignment;
import ucsf.sod.xo.ChairScheduler.ChairMapper;
import ucsf.sod.xo.ChairScheduler.ChairPosition;
import ucsf.sod.xo.XOGridUtils.ClinicStatus;
import ucsf.sod.xo.XOGridUtils.ClinicStatus.OpenMode;
import ucsf.sod.xo.calendar.AcademicCalendar;
import ucsf.sod.xo.calendar.AcademicCalendar.Quarter;
import ucsf.sod.xo.objects.GroupPractice;
import ucsf.sod.xo.objects.PerioSession;
import ucsf.sod.xo.objects.Rotation;
import ucsf.sod.xo.objects.Student;
import ucsf.sod.xo.objects.Student.UpperLower;
import ucsf.sod.xo.scheduler.CapacityScenario;
import ucsf.sod.xo.scheduler.ERNPEScheduler;
import ucsf.sod.xo.scheduler.Pairing;
import ucsf.sod.xo.scheduler.PairingType;
import ucsf.sod.xo.scheduler.CapacityScenario.ScenarioMode;

public class BaselineSchedule2023 {

	public static void main(String[] args) throws Exception {

		String xoGridFilePath = args[0];
		String statisticsDirPath = args[1];
		String outputFileName = args[2];
		
		if(AcademicCalendar.CURRENT_YEAR != AcademicCalendar.AY2023_2024) {
			AcademicCalendar.CURRENT_YEAR = AcademicCalendar.AY2023_2024;
			System.err.println("Future flag not set; setting AcademicCalendar to " + AcademicCalendar.CURRENT_YEAR);
		}
		
		XOGridReader reader = XOGridReader2023.of(SODUtil.openWorkbook(xoGridFilePath));
		System.out.println("X-O Grids Loaded");
		
		//XOGridOptimizer.countsWithLink(reader).forEach((s, i) -> System.out.println(s.id + "\t" + s.first + "\t" + s.practice + "\t" + i));
		//XOGridOptimizer.printKeyDates(reader);
		
		SODExcelFactory domestic = new BaselineSchedule2023().generate(reader);
		File f = SODUtil.getTimestampedFile(statisticsDirPath, outputFileName, "xlsx");
		domestic.export(f);
		Desktop.getDesktop().open(f);
	}
	
	public SODExcelFactory generate(XOGridReader reader) throws IOException {
		LocalDate startDate = reader.getFirstDate();
		{
			DayOfWeek day = startDate.getDayOfWeek();
			if(day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
				startDate = SODDateUtils.ceilingToNextMonday(startDate);
			}
		}
		LocalDate endDate = SODDateUtils.ceilingToDayOfWeek(reader.getLastDate(), DayOfWeek.FRIDAY);
		AcademicCalendar calendar = reader.getAcademicCalendar();
		
		Map<DatedSession, Map<GroupPractice, List<Pairing>>> dailyWorkforce = new TreeMap<DatedSession, Map<GroupPractice, List<Pairing>>>();
		Map<DatedSession, Map<GroupPractice, Map<ChairPosition, ChairAssignment>>> dailyLayout = new TreeMap<DatedSession, Map<GroupPractice, Map<ChairPosition, ChairAssignment>>>();
		generatePredoc(reader, startDate, endDate, dailyWorkforce, dailyLayout);		

		Map<DatedSession, List<Pairing>> dailyPerioWorkforce = new TreeMap<DatedSession, List<Pairing>>();
		Map<DatedSession, Map<ChairPosition, ChairAssignment>> dailyPerioLayout = new TreeMap<DatedSession, Map<ChairPosition, ChairAssignment>>();
		generatePerio(reader, startDate, endDate, calendar, dailyPerioWorkforce, dailyPerioLayout);

		{
			Map<Student, List<ChairAssignment>> assignmentsByStudent = 
				dailyLayout.entrySet().stream() 					// every session
					.flatMap(e -> e.getValue().entrySet().stream()) // every group practice 
					.flatMap(e -> e.getValue().entrySet().stream()) // every chair position
					.map(e -> e.getValue()) 						// get the chair assignment
					.filter(c -> c != null)
					.collect(Collectors.groupingBy(
						c -> c.getAssigned(),
						TreeMap::new,
						Collectors.toCollection(() -> new ArrayList<ChairAssignment>())
					));

			Map<GroupPractice, Map<Student, List<ChairAssignment>>> assignmentsByGroupPractice = assignmentsByStudent.entrySet().stream()
				.collect(Collectors.groupingBy(
					e -> e.getKey().practice,
					XOGridUtils::createGroupPracticeEnumMap,
					Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)
				));
	
			for(GroupPractice practice : XOGridUtils.ALL_PRACTICES) {
				Map<Student, List<ChairAssignment>> base = assignmentsByGroupPractice.get(practice);
				if(practice == GroupPractice.F) {
					ChairStatistics d4 = ChairStatistics.of(base, e -> e.getKey().isD4() || e.getKey().isID4());
					
					System.out.println("\n\nGP-" + practice + " BEFORE BALANCING");
					System.out.println("\tD4\t" + d4.average + "+/-" + d4.stdDev);
					
				} else {
					ChairStatistics d3 = ChairStatistics.of(base, e -> e.getKey().isD3() || e.getKey().isID3());
					ChairStatistics d4 = ChairStatistics.of(base, e -> e.getKey().isD4() || e.getKey().isID4());
					ChairStatistics all = ChairStatistics.of(base, e -> true);
					
					System.out.println("\n\nGP-" + practice + " BEFORE BALANCING");
					System.out.println("\tD3\t" + d3.average + "+/-" + d3.stdDev);
					System.out.println("\tD4\t" + d4.average + "+/-" + d4.stdDev);
					System.out.println("\tAll\t" + all.average + "+/-" + all.stdDev);
				}
			}
			
			balanceByGroupPractice(assignmentsByStudent);
			
			// TODO: make balanceByGroupPractice() update the given map so that this call doesn't have to happen again
			assignmentsByStudent = 
				dailyLayout.entrySet().stream() 					// every session
					.flatMap(e -> e.getValue().entrySet().stream()) // every group practice 
					.flatMap(e -> e.getValue().entrySet().stream()) // every chair position
					.map(e -> e.getValue()) 						// get the chair assignment
					.filter(c -> c != null)
					.collect(Collectors.groupingBy(
						c -> c.getAssigned(),
						TreeMap::new,
						Collectors.toList()
					));
			
			assignmentsByGroupPractice = assignmentsByStudent.entrySet().stream()
				.collect(Collectors.groupingBy(
					e -> e.getKey().practice,
					XOGridUtils::createGroupPracticeEnumMap,
					Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)
				));

			for(GroupPractice practice : XOGridUtils.ALL_PRACTICES) {
				Map<Student, List<ChairAssignment>> base = assignmentsByGroupPractice.get(practice);
				if(practice == GroupPractice.F) {
					ChairStatistics d4 = ChairStatistics.of(base, e -> e.getKey().isD4() || e.getKey().isID4());
					
					System.out.println("\n\nGP-" + practice + " AFTER BALANCING");
					System.out.println("\tD4\t" + d4.average + "+/-" + d4.stdDev);
					
				} else {
					ChairStatistics d3 = ChairStatistics.of(base, e -> e.getKey().isD3() || e.getKey().isID3());
					ChairStatistics d4 = ChairStatistics.of(base, e -> e.getKey().isD4() || e.getKey().isID4());
					ChairStatistics all = ChairStatistics.of(base, e -> true);
					
					System.out.println("\n\nGP-" + practice + " AFTER BALANCING");
					System.out.println("\tD3\t" + d3.average + "+/-" + d3.stdDev);
					System.out.println("\tD4\t" + d4.average + "+/-" + d4.stdDev);
					System.out.println("\tAll\t" + all.average + "+/-" + all.stdDev);
				}
			}
		}		
		
		return BaselineAxiumReport.generate(
			dailyWorkforce,
			dailyLayout,
			dailyPerioWorkforce,
			dailyPerioLayout,
			reader,
			npvRotation,
			erRotation,
			orphanPairings
		);
	}
	
	/**
	 * Determines if a given clinic date should allow a D2 to provide
	 * @param date date of question
	 * @param d2ClinicBegins date where D2s can provide in clinic
	 * @return
	 */
	private boolean canD2sProvide(LocalDate date, LocalDate d2ClinicBegins) {
		return SODDateUtils.dateIsOnOrAfter(date, d2ClinicBegins) && (date.getDayOfWeek() == DayOfWeek.WEDNESDAY || date.getDayOfWeek() == DayOfWeek.FRIDAY);
	}
	
	private Map<Student, List<DatedSession>> d2ProviderCount = new TreeMap<Student, List<DatedSession>>();

	private void generatePredoc(
		XOGridReader reader, 
		LocalDate startDate, 
		LocalDate endDate, 
		Map<DatedSession, Map<GroupPractice, List<Pairing>>> dailyWorkforce, 
		Map<DatedSession, Map<GroupPractice, Map<ChairPosition, ChairAssignment>>> dailyLayout 
	) {

 		AcademicCalendar calendar = reader.getAcademicCalendar();
		for(GroupPractice practice : XOGridUtils.ALL_PRACTICES) {
		//for(GroupPractice practice : List.of(GroupPractice.F)) {
			
			System.out.println("\n*** Generating schedule for GP-" + practice + " ***\n");
			
			// Iterate through all the dates
			for(LocalDate date = startDate; SODDateUtils.dateIsOnOrBefore(date, endDate); date = date.plusDays(date.getDayOfWeek() == DayOfWeek.FRIDAY ? 3 : 1)) {
				ClinicStatus status = calendar.get(date);
				if(status.status == OpenMode.CLOSED) {
					System.out.println("Clinic is closed on " + date + "\n");
					continue;
				}
				
				Set<Student> students = Rotation.CLINIC.getStudentsByPractice(practice, date);
				for(GenericPeriod period : List.of(GenericPeriod.GENERIC_AM, GenericPeriod.GENERIC_PM)) {

					// Optimize if we know there are no students
					if((period.isAM() && status.status == OpenMode.PM_ONLY) ||
					   (period.isPM() && status.status == OpenMode.AM_ONLY)
					) {
						continue;
					} else if(reader.inHuddleDate(practice, date, period)) {
						continue;
					}
					
					// Filter out the students that are not available
					Collection<Student> remaining = students.stream().filter(getOnlyAvailableStudentsSelector(reader, date, period)).collect(Collectors.toCollection(TreeSet::new));
					
					// Add D2 students if they are available
					if(canD2sProvide(date, reader.getD2ClinicBegins())) { // TODO: push one week later
						practice.getStudentsStream(Student::isD2)
							.filter(s -> remaining.contains(s.getPrimaryLink()) || remaining.contains(s.getSecondaryLink()))
							.forEach(s -> remaining.add(s));
					}
					
					// Create initial pairing
					List<Pairing> pairs = Pairing.pairUp(remaining);

					// Evaluate capacity is met and finalize pairing accordingly
					int chairCapacity = 13;
					/*
					if(reader.isD4LectureDate(date) && period.isAM()) {
						chairCapacity = 8;
						
						for(Pairing p : pairs.stream().filter(p -> p.type == PairingType.ORPHAN).collect(Collectors.toList())) {
							if(p.getSoloStudent().getPartner() == Student.PLACEHOLDER) {
								pairs.remove(p);
								pairs.add(p.correctType());
							}
						}
					} else if(date.getDayOfWeek() == DayOfWeek.FRIDAY && period.isPM()) {
						chairCapacity = 8;
						if(practice == GroupPractice.F) {
							chairCapacity = 20;
						}
						
						for(Pairing p : pairs.stream().filter(p -> p.type == PairingType.ORPHAN).collect(Collectors.toList())) {
							if(p.getSoloStudent().getPartner() == Student.PLACEHOLDER) {
								pairs.remove(p);
								pairs.add(p.correctType());
							}
						}
					} else if(reader.isClinicBreak(date)) {
						chairCapacity = 8;
						if(practice == GroupPractice.F) {
							chairCapacity = 12;
						}
						
						for(Pairing p : pairs.stream().filter(p -> p.type == PairingType.ORPHAN).collect(Collectors.toList())) {
							if(p.getSoloStudent().getPartner() == Student.PLACEHOLDER) {
								pairs.remove(p);
								pairs.add(p.correctType());
							}
						}
					} else {
						chairCapacity = 13;
					}
					*/
					
					pairs = finalizePairings(
						pairs, 
						reader, 
						date, 
						period,
						chairCapacity,
						practice == GroupPractice.F ? getScenarioSelectorInternational(reader, date, period, chairCapacity) : getScenarioSelectorDomestic(reader, date, period, chairCapacity) 
					);

					DatedSession dSession = DatedSession.of(date, period);
					Map<GroupPractice, List<Pairing>> map = dailyWorkforce.get(dSession);
					if(map == null) {
						dailyWorkforce.put(dSession, map = XOGridUtils.createGroupPracticeEnumMap());
					}
					map.put(practice, pairs);
				}
			}

			// Assign ER and NPE for the practice
			Map<DatedSession, Collection<Pairing>> allpairs = dailyWorkforce.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getOrDefault(practice, List.of())));
			assignERAndNPE(practice, allpairs, reader);
			
			// Assign the chairs
			for(DatedSession date : dailyWorkforce.keySet()) {
				List<Pairing> pairs = dailyWorkforce.get(date).getOrDefault(practice, List.of());
				if(pairs.size() == 0) {
					System.err.println("There are no chairs to assign on " + date + " " + date.session.getPeriod().getMeridian());
				} else {
					
					// Sort the ER, NPE chairs first; then, prioritize D2 pairings first when applicable
					Comparator<Pairing> comparator = ER_NPE_Priority;
					if(canD2sProvide(date.date, reader.getD2ClinicBegins())) {
						comparator = comparator.thenComparing(d2PriorityBalanced);
					}
					Collections.sort(pairs, comparator);
					
					//printPairings(pairs, date.date, date.getPeriod(), covidCoverage.getOrDefault(date, Student.PLACEHOLDER));
					
					Map<ChairPosition, ChairAssignment> floormap;
					if(date.getDayOfWeek() == DayOfWeek.FRIDAY && date.isPM() && date.date.isBefore(reader.getSpringQuarterStart())) {
						ChairMapper friMapper;
						Quarter q = AcademicCalendar.CURRENT_YEAR.toQuarter(date.date);
						switch(q) {
						case SUMMER:
							friMapper = ChairMapper.ofIterativeFull(ChairScheduler.friPMChairMapperSummer.get(practice)); break;
						case FALL:
						case WINTER:
							friMapper = ChairMapper.ofIterativeFull(ChairScheduler.friPMChairMapper.get(practice)); break;
						case SPRING:
							friMapper = ChairMapper.ofIterativeFull(ChairScheduler.chairMapper.get(practice)); break;
						default:
							throw new RuntimeException("Unexpected quarter for ["+date+"]: " + q);
						}

						floormap = ChairScheduler.FRI_PM.assignChairs(date, pairs, friMapper, p -> getProviderAndLabel(p, practice, date, reader.getPriority(date.date)));
					} else {
						floormap = ChairScheduler.DEFAULT.assignChairs(date, pairs, ChairMapper.ofIterativeFull(ChairScheduler.chairMapper.get(practice)), p -> getProviderAndLabel(p, practice, date, reader.getPriority(date.date)));
					}
					
					// Floormap count being less than pairs count means there are pairs that do not get a chair 
					if(floormap.size() < pairs.size()) {
						List<Pairing> orphans = new ArrayList<Pairing>(pairs);
						if(!orphans.removeAll(floormap.values().stream().map(a -> a.pairing).collect(Collectors.toList()))) {
							throw new RuntimeException("List was not modified");
						}
						
						Map<DatedSession, List<Pairing>> map = orphanPairings.get(practice);
						List<Pairing> l = map.get(date);
						if(l != null) {
							System.err.println("Replacing orphan list on " + date);
							orphans.addAll(l);
						}
						map.put(date, orphans);	
					}
					
					// Count all the D2 chairs assigned
					floormap.values().forEach(a -> {
						if(a != null) {
							Student _student = a.getAssigned();
							if(_student.isD2()) {
								List<DatedSession> l = d2ProviderCount.get(_student);
								if(l == null) {
									d2ProviderCount.put(_student, l = new ArrayList<DatedSession>());
								}
								l.add(date);
							}
						}
					});

					Map<GroupPractice, Map<ChairPosition, ChairAssignment>> layout = dailyLayout.get(date);
					if(layout == null) {
						dailyLayout.put(date, layout = XOGridUtils.createGroupPracticeEnumMap());
					}
					
					
					layout.put(practice, floormap);
				}
			}
		}
	}

	private void generatePerio(XOGridReader reader, LocalDate startDate, LocalDate endDate, AcademicCalendar calendar, Map<DatedSession, List<Pairing>> dailyWorkforce, Map<DatedSession, Map<ChairPosition, ChairAssignment>> dailyLayout) {

		// Figure out which students are in which session
		Map<PerioSession, Set<Student>> perioInfo = new HashMap<PerioSession, Set<Student>>();
		for(GroupPractice p : XOGridUtils.PRACTICES) {
			p.getStudentsStream(s -> s.isD3() || s.isID3()).forEach(s -> {
				PerioSession session = s.d3perio;
				Set<Student> _s = perioInfo.get(session);
				if(_s == null) {
					perioInfo.put(session, _s = new HashSet<Student>());
				}
				_s.add(s);
			});
		}
		
		for(LocalDate date = startDate; SODDateUtils.dateIsOnOrBefore(date, endDate); date = date.plusDays(date.getDayOfWeek() == DayOfWeek.FRIDAY ? 3 : 1)) {
			
			ClinicStatus status = calendar.get(date);
			if(status.status == OpenMode.CLOSED) {
				System.out.println("Clinic is closed on " + date + "\n");
				continue;
			}
			
			for(GenericPeriod period : List.of(GenericPeriod.GENERIC_AM, GenericPeriod.GENERIC_PM)) {
				{
					DayOfWeek day = date.getDayOfWeek();
					if(day == DayOfWeek.MONDAY || day == DayOfWeek.WEDNESDAY || day == DayOfWeek.FRIDAY) {
						continue;
					}
				}
				
				// Get pairings
				PerioSession session = PerioSession.toSession(date, period);
				List<Pairing> pairs = new ArrayList<Pairing>();
				for(Student _s : perioInfo.getOrDefault(session, Set.of())) {
					if((_s.isD3() && date.isBefore(reader.getD3PerioClinicBegins())) ||
					   (_s.isID3() && date.isBefore(reader.getID3PerioClinicBegins()))
					) {
						continue;
					} else if(reader.getRotationByStudent(_s, date, null) == Rotation.CLINIC) {
						if(reader.getPriority(date) == UpperLower.UPPER && _s.isUpperStudent()) {
							pairs.add(Pairing.of(_s, Student.PLACEHOLDER, PairingType.ORPHAN));
						} else if(reader.getPriority(date) == UpperLower.LOWER && _s.isLowerStudent()) {
							pairs.add(Pairing.of(_s, Student.PLACEHOLDER, PairingType.ORPHAN));
						}
					}
				}
				
				if(pairs.size() == 0) {
					System.err.println("There are no perio chairs to assign on " + date.getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH) + " " + date + " " + period.getMeridian());
					continue;
				}

				//printPairings(pairs, date, period, Student.PLACEHOLDER);
				
				DatedSession _session = DatedSession.of(date, period);
				Map<ChairPosition, ChairAssignment> floormap = ChairScheduler.PERIO.assignChairs(_session, pairs, ChairMapper.ofIterativeFull(ChairScheduler.perioChairMapper), p -> Pair.of(p.getSoloStudent(), ""));
				dailyLayout.put(_session, floormap);
			}
		}
	}

	private Map<DatedSession, Map<GroupPractice, Student>> erRotation = new TreeMap<DatedSession, Map<GroupPractice, Student>>();
	private Map<DatedSession, Map<GroupPractice, Student>> npvRotation = new TreeMap<DatedSession, Map<GroupPractice, Student>>();

	private void assignERAndNPE(GroupPractice practice, Map<DatedSession, Collection<Pairing>> allpairs, XOGridReader reader) {
		
		System.out.println("Generating ER schedule");
		
		Set<DatedSession> erAssignedSessions = new TreeSet<DatedSession>();
		System.out.println("Determine dates responsible");
		{
			List<Map<Pair<DayOfWeek, String>, List<GroupPractice>>> erRotationOrder = List.of(
				Map.ofEntries(
					Map.entry(Pair.of(DayOfWeek.MONDAY, "AM"), List.of(GroupPractice.A, GroupPractice.B)),
					Map.entry(Pair.of(DayOfWeek.MONDAY, "PM"), List.of(GroupPractice.C, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "AM"), List.of(GroupPractice.E, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "PM"), List.of(GroupPractice.A, GroupPractice.B)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "AM"), List.of(GroupPractice.C, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "PM"), List.of(GroupPractice.E, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "AM"), List.of(GroupPractice.A, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "PM"), List.of(GroupPractice.B, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "AM"), List.of(GroupPractice.D, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "PM"), List.of(GroupPractice.A, GroupPractice.C))
				),
				Map.ofEntries(
					Map.entry(Pair.of(DayOfWeek.MONDAY, "AM"), List.of(GroupPractice.B, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.MONDAY, "PM"), List.of(GroupPractice.D, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "AM"), List.of(GroupPractice.A, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "PM"), List.of(GroupPractice.B, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "AM"), List.of(GroupPractice.C, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "PM"), List.of(GroupPractice.A, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "AM"), List.of(GroupPractice.B, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "PM"), List.of(GroupPractice.C, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "AM"), List.of(GroupPractice.A, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "PM"), List.of(GroupPractice.B, GroupPractice.D))
				),
				Map.ofEntries(
					Map.entry(Pair.of(DayOfWeek.MONDAY, "AM"), List.of(GroupPractice.C, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.MONDAY, "PM"), List.of(GroupPractice.A, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "AM"), List.of(GroupPractice.B, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "PM"), List.of(GroupPractice.C, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "AM"), List.of(GroupPractice.A, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "PM"), List.of(GroupPractice.B, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "AM"), List.of(GroupPractice.D, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "PM"), List.of(GroupPractice.A, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "AM"), List.of(GroupPractice.B, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "PM"), List.of(GroupPractice.D, GroupPractice.E))
				),

				Map.ofEntries(
					Map.entry(Pair.of(DayOfWeek.MONDAY, "AM"), List.of(GroupPractice.C, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.MONDAY, "PM"), List.of(GroupPractice.E, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "AM"), List.of(GroupPractice.A, GroupPractice.B)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "PM"), List.of(GroupPractice.C, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "AM"), List.of(GroupPractice.E, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "PM"), List.of(GroupPractice.A, GroupPractice.B)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "AM"), List.of(GroupPractice.B, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "PM"), List.of(GroupPractice.D, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "AM"), List.of(GroupPractice.A, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "PM"), List.of(GroupPractice.B, GroupPractice.E))
				),
				Map.ofEntries(
					Map.entry(Pair.of(DayOfWeek.MONDAY, "AM"), List.of(GroupPractice.D, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.MONDAY, "PM"), List.of(GroupPractice.A, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "AM"), List.of(GroupPractice.B, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "PM"), List.of(GroupPractice.C, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "AM"), List.of(GroupPractice.A, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "PM"), List.of(GroupPractice.B, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "AM"), List.of(GroupPractice.C, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "PM"), List.of(GroupPractice.A, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "AM"), List.of(GroupPractice.B, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "PM"), List.of(GroupPractice.C, GroupPractice.F))
				),
				Map.ofEntries(
					Map.entry(Pair.of(DayOfWeek.MONDAY, "AM"), List.of(GroupPractice.A, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.MONDAY, "PM"), List.of(GroupPractice.B, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "AM"), List.of(GroupPractice.C, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "PM"), List.of(GroupPractice.A, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "AM"), List.of(GroupPractice.B, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "PM"), List.of(GroupPractice.D, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "AM"), List.of(GroupPractice.A, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "PM"), List.of(GroupPractice.B, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "AM"), List.of(GroupPractice.D, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "PM"), List.of(GroupPractice.A, GroupPractice.F))
				),
				
				Map.ofEntries(
					Map.entry(Pair.of(DayOfWeek.MONDAY, "AM"), List.of(GroupPractice.E, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.MONDAY, "PM"), List.of(GroupPractice.A, GroupPractice.B)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "AM"), List.of(GroupPractice.C, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "PM"), List.of(GroupPractice.E, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "AM"), List.of(GroupPractice.A, GroupPractice.B)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "PM"), List.of(GroupPractice.C, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "AM"), List.of(GroupPractice.D, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "PM"), List.of(GroupPractice.A, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "AM"), List.of(GroupPractice.B, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "PM"), List.of(GroupPractice.D, GroupPractice.F))
				),
				Map.ofEntries(
					Map.entry(Pair.of(DayOfWeek.MONDAY, "AM"), List.of(GroupPractice.A, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.MONDAY, "PM"), List.of(GroupPractice.B, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "AM"), List.of(GroupPractice.C, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "PM"), List.of(GroupPractice.A, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "AM"), List.of(GroupPractice.B, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "PM"), List.of(GroupPractice.C, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "AM"), List.of(GroupPractice.A, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "PM"), List.of(GroupPractice.B, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "AM"), List.of(GroupPractice.C, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "PM"), List.of(GroupPractice.A, GroupPractice.E))
				),
				Map.ofEntries(
					Map.entry(Pair.of(DayOfWeek.MONDAY, "AM"), List.of(GroupPractice.B, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.MONDAY, "PM"), List.of(GroupPractice.C, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "AM"), List.of(GroupPractice.A, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.TUESDAY, "PM"), List.of(GroupPractice.B, GroupPractice.D)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "AM"), List.of(GroupPractice.D, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.WEDNESDAY, "PM"), List.of(GroupPractice.A, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "AM"), List.of(GroupPractice.B, GroupPractice.C)),
					Map.entry(Pair.of(DayOfWeek.THURSDAY, "PM"), List.of(GroupPractice.D, GroupPractice.E)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "AM"), List.of(GroupPractice.A, GroupPractice.F)),
					Map.entry(Pair.of(DayOfWeek.FRIDAY, "PM"), List.of(GroupPractice.B, GroupPractice.C))
				)
			);
			
			int index = 0;
			Map<Pair<DayOfWeek, String>, List<GroupPractice>> erOrder = erRotationOrder.get(index);
			AcademicCalendar calendar = reader.getAcademicCalendar();
			LocalDate endDate = reader.getLastDate();
			for(LocalDate date = reader.getFirstDate(); SODDateUtils.dateIsOnOrBefore(date, endDate); date = date.plusDays(date.getDayOfWeek() == DayOfWeek.FRIDAY ? 3 : 1)) {
				ClinicStatus status = calendar.get(date);
				if(status.status == OpenMode.CLOSED) {
					continue;
				}
				
				//Set<Student> students = Rotation.CLINIC.getStudentsByPractice(practice, date);
				for(GenericPeriod period : List.of(GenericPeriod.GENERIC_AM, GenericPeriod.GENERIC_PM)) {
					boolean onER = erOrder.get(Pair.of(date.getDayOfWeek(), period.getMeridian())).contains(practice);
					
					// Optimize if we know there are no students
					if((period.isAM() && status.status == OpenMode.PM_ONLY) ||
					   (period.isPM() && status.status == OpenMode.AM_ONLY)
					) {
						if(onER) {
							System.err.println("GP-" + practice + " unable to provide students for " + date + " " + period.getMeridian() + " due to clinic closure");
						}
						continue;
					} else if(reader.inHuddleDate(practice, date, period)) {
						if(onER) {
							System.err.println("GP-" + practice + " unable to provide students for " + date + " " + period.getMeridian() + " due to Huddle");
						}
						continue;
					}
					
					if(practice == GroupPractice.F && onER && SODDateUtils.dateIsOnOrAfter(date, reader.getSpringQuarterStart())) {
						continue;
					} else if(!onER) {
						List<GroupPractice> onCall = erOrder.get(Pair.of(date.getDayOfWeek(), period.getMeridian()));
						if(!onCall.contains(GroupPractice.F)) {
							continue;
						} else if(date.isBefore(reader.getSpringQuarterStart())) {
							continue;
						} else {
							GroupPractice onDeck = onCall.get(0);
							if(onDeck == practice) {
								throw new RuntimeException("Logic should have recognized this practice earlier");
							} else if(onDeck == GroupPractice.F) {
								onDeck = onCall.get(1);
							}
							
							// Cannot have the same group cover the same ER session twice
							switch(onDeck) {
							case A: onDeck = GroupPractice.B; break;
							case B: onDeck = GroupPractice.C; break;
							case C: onDeck = GroupPractice.D; break;
							case D: onDeck = GroupPractice.E; break;
							case E: onDeck = GroupPractice.A; break;
							case F:
								throw new RuntimeException("Logic shouldn't make it here");
							}
							
							if(onDeck != practice) {
								continue;
							}
						}
					}

					erAssignedSessions.add(DatedSession.of(date, period));
				}
				
				if(date.getDayOfWeek() == DayOfWeek.FRIDAY) {
					erOrder = erRotationOrder.get(index = (++index % erRotationOrder.size()));
				}
			}
		}		
		System.out.println("Done determine dates responsible");
		
		ERNPEScheduler erScheduler = ERNPEScheduler.buildERScheduler(
			practice, 
			allpairs.entrySet().parallelStream().filter(e -> erAssignedSessions.contains(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
			ChairScheduler.getERMoratorium(reader),
			s -> false,
			session -> reader.getPriority(session.date)
		);
		Map<DatedSession, Student> erAssignmentOrder = erScheduler.schedule(); 
		Map<Student, Integer> erCount = new TreeMap<Student, Integer>();
		erAssignmentOrder.forEach((session, student) -> {
			synchronized(erRotation) {
				Map<GroupPractice, Student> m = erRotation.get(session);
				if(m == null) {
					erRotation.put(session, m = XOGridUtils.createGroupPracticeEnumMap());
				}
				m.put(practice, student);
				erCount.put(student, (erCount.getOrDefault(student, 0) + 1));
			}
		});
		System.out.println("Generating ER schedule completed");

		System.out.println("Generating NPE schedule for GP-"+practice);
		ERNPEScheduler npeScheduler = ERNPEScheduler.buildNPEScheduler(
			practice, 
			allpairs,
			ChairScheduler.getNPEMoratorium(reader.getAcademicCalendar()),
			s -> false,
			session -> reader.getPriority(session.date)					
		);
		
		Map<DatedSession, Student> npeAssignmentOrder = npeScheduler.schedule();
		//npeScheduler.getRemainingMap().forEach((s, i) -> System.err.println(s.id + "\t" + i));
		Map<Student, Integer> npeCount = new TreeMap<Student, Integer>();
		npeAssignmentOrder.forEach((session, student) -> {
			synchronized(npvRotation) {
				Map<GroupPractice, Student> m = npvRotation.get(session);
				if(m == null) {
					npvRotation.put(session, m = XOGridUtils.createGroupPracticeEnumMap());
				}
				m.put(practice, student);
				npeCount.put(student, (npeCount.getOrDefault(student, 0) + 1));
			}
		});
		//npeCount.forEach((s, i) -> System.err.println(s.id + "\t" + i));
		System.out.println("Generating NPE schedule completed");
	}

	private static class ChairStatistics {
		public final int average;
		public final int stdDev;
		public final int oneStdDevBelow;
		public final int oneStdDevAbove;
		
		public ChairStatistics(int average, int stdDev) {
			this.average = average;
			this.stdDev = stdDev;
			this.oneStdDevBelow = average - stdDev;
			this.oneStdDevAbove = average + stdDev;
		}
		
		public static ChairStatistics of(Map<Student, List<ChairAssignment>> assignmentsByStudent, Predicate<Map.Entry<Student, List<ChairAssignment>>> filter) {
			return new ChairStatistics(
				(int)(assignmentsByStudent.entrySet().stream().filter(filter).mapToInt(e -> e.getValue().size()).average().getAsDouble()),
				(int)(Math.round(Math.sqrt(assignmentsByStudent.entrySet().stream().filter(filter).mapToDouble(e -> e.getValue().size()).boxed().collect(SODUtil.VARIANCE_COLLECTOR))))
			);
		}
	}
	
	private static enum AboveBelow {
		D4_ABOVE,
		D3_ABOVE,
		NEITHER,
		D3_BELOW,
		D4_BELOW
	}

	/**
	 * Rebalances chair assignments by student. Does not update student association to the chair assignment.
	 * @param assignmentsByStudent
	 */
	private void balanceByGroupPractice(Map<Student, List<ChairAssignment>> assignmentsByStudent) {
		Map<GroupPractice, Map<Student, List<ChairAssignment>>> assignmentsByGroupPractice = assignmentsByStudent.entrySet().stream()
			.collect(Collectors.groupingBy(
					e -> e.getKey().practice,
					XOGridUtils::createGroupPracticeEnumMap,
					Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)
			));

		for(GroupPractice p : XOGridUtils.ALL_PRACTICES) {
			
			if(p == GroupPractice.F) {
				continue;
			}
			
			Map<Student, List<ChairAssignment>> base = assignmentsByGroupPractice.get(p);
			{
				ChairStatistics d3 = ChairStatistics.of(base, e -> e.getKey().isD3() || e.getKey().isID3());
				ChairStatistics d4 = ChairStatistics.of(base, e -> e.getKey().isD4() || e.getKey().isID4());
				ChairStatistics all = ChairStatistics.of(base, e -> true);
				
				Map<AboveBelow, Set<Student>> sortedStudents = assignmentsByStudent.entrySet().stream().collect(
					Collectors.groupingBy(
						e -> {
							Student s = e.getKey();
							int size = e.getValue().size();
							
							if(s.isD3() || s.isID3()) {
								if(size <= d3.oneStdDevBelow) {
									return AboveBelow.D3_BELOW;
								} else if(size > d3.oneStdDevAbove) {
									return AboveBelow.D3_ABOVE;
								} else {
									return AboveBelow.NEITHER;
								}
							} else if(s.isD4() || s.isID4()) {
								if(size <= d4.oneStdDevBelow) {
									return AboveBelow.D4_BELOW;
								} else if(size > d4.oneStdDevAbove) {
									return AboveBelow.D4_ABOVE;
								} else {
									return AboveBelow.NEITHER;
								}
							} else if(s.isD2()) {
								return AboveBelow.NEITHER;
							} else {
								throw new RuntimeException("Unexpected student for statistics: " + s);
							}
						},
						Collectors.mapping(Map.Entry::getKey, Collectors.toSet())
					)
				);
				
				// Builds a tuple of Student, Needed to get to average, and Pool available to swap
				List<Triple<Student, Integer, Integer>> swapNeeded = base.keySet().stream().filter(s -> {
						if((s.isD3() || s.isID3()) && (sortedStudents.getOrDefault(AboveBelow.D3_BELOW, Set.of()).contains(s) && sortedStudents.getOrDefault(AboveBelow.D4_ABOVE, Set.of()).contains(s.getPrimaryLink()))) {
							return true;
						} else if((s.isD4() || s.isID4()) && (sortedStudents.getOrDefault(AboveBelow.D4_BELOW, Set.of()).contains(s) && sortedStudents.getOrDefault(AboveBelow.D3_ABOVE, Set.of()).contains(s.getPrimaryLink()))) {
							return true;
						}
						
						return false;
					})
					.map(s -> {
						
						ChairStatistics stats;
						ChairStatistics link_stats;
						if(s.isD3() || s.isID3()) {
							stats = d3;
							link_stats = d4;
						} else if(s.isD4() || s.isID4()) {
							stats = d4;
							link_stats = d3;
						} else {
							throw new RuntimeException("Student is of unknown year: " + s);
						}
						
						int need = stats.average - assignmentsByStudent.get(s).size();
						int surplus = assignmentsByStudent.get(s.getPrimaryLink()).size() - link_stats.average;

						return Triple.of(s, need, surplus);
					})
					.collect(Collectors.toList());

//				info("\nStudents to balance: ");
//				swapNeeded.forEach(t -> {
//					info("\t" + t.getLeft().id + " needs " + t.getMiddle() + ", " + t.getLeft().getPrimaryLink() + " can give " + t.getRight());
//				});

				swapProviders(assignmentsByStudent, swapNeeded);
			}
		}
	}
	
	private void swapProviders(Map<Student, List<ChairAssignment>> assignmentsByStudent, List<Triple<Student, Integer, Integer>> swapNeeded) {

		for(Triple<Student, Integer, Integer> t : swapNeeded) {
			Student s = t.getLeft();
			int need = t.getMiddle();
			int surplus = t.getRight();
			Student link = s.getPrimaryLink();
			
			if(surplus <= 0) {
				System.err.println("Unable to swap for " + s.id + " because there is no pool available: " + surplus);
				continue;
			}
			
			// Keep any chair assignments with both s and link
			Map<DatedSession, ChairAssignment> s_assign = assignmentsByStudent.get(s).stream()
				.filter(c -> 
					((c.pairing.a == s && c.pairing.b == link) || (c.pairing.a == link && c.pairing.b == s)) &&
					c.pairing.label == null
				).collect(Collectors.toMap(c -> c.session, Function.identity()));
			Map<DatedSession, ChairAssignment> l_assign = assignmentsByStudent.get(link).stream()
				.filter(c -> 
					((c.pairing.a == s && c.pairing.b == link) || (c.pairing.a == link && c.pairing.b == s)) &&
					c.pairing.label == null
				).collect(Collectors.toMap(c -> c.session, Function.identity()));

			List<DatedSession> s_dates = new ArrayList<DatedSession>(s_assign.keySet());
			List<DatedSession> l_dates = new ArrayList<DatedSession>(l_assign.keySet());
			
			XOGridUtils.shuffle(l_dates);
			
			int totalTransfer = Math.min(need, surplus);
//			info(s.id + " needs " + need + ", can receive " + totalTransfer + "/"+l_dates.size()+" from link " + link.id + ", leaving link with " + (assignmentsByStudent.get(link).size() - totalTransfer) + " chairs ");

			int count = 0;
			for(DatedSession date : l_dates.subList(0, totalTransfer)) {
				ChairAssignment assignment = l_assign.get(date);
				if(assignment.hasToggled()) {
					throw new RuntimeException("Assignment has been toggled once on " + date + " for " + s.id);
				} else {
					Student prev = assignment.getAssigned();
					if(!assignmentsByStudent.get(prev).remove(assignment)) {
						throw new RuntimeException("Assignment was not associated with the prev student");
					}
					assignment.toggleAssigned();
					assignmentsByStudent.get(assignment.getAssigned()).add(assignment);
					count++;
				}
			}
//			info("Toggled " + count);
		}
	}
	
	private Map<GroupPractice, Map<DatedSession, List<Pairing>>> orphanPairings = Map.ofEntries(
		Map.entry(GroupPractice.A, new TreeMap<DatedSession, List<Pairing>>()),
		Map.entry(GroupPractice.B, new TreeMap<DatedSession, List<Pairing>>()),
		Map.entry(GroupPractice.C, new TreeMap<DatedSession, List<Pairing>>()),
		Map.entry(GroupPractice.D, new TreeMap<DatedSession, List<Pairing>>()),
		Map.entry(GroupPractice.E, new TreeMap<DatedSession, List<Pairing>>()),
		Map.entry(GroupPractice.F, new TreeMap<DatedSession, List<Pairing>>())
	);
		
	/**
	 * Determine if any pairs need to be split to reach capacity
	 * @param pairs pairs to finalize; pairs are assumed to be based off of the initial pool of students
	 * @param reader
	 * @param date
	 * @param period
	 * @return a new list of finalized list of pairs to assign chairs to;
	 */
	private List<Pairing> finalizePairings(List<Pairing> pairs, XOGridReader reader, LocalDate date, Period period, int chairCapacity, Function<CapacityScenario, ScenarioMode> capacity) {

		// Remove orphans if this is not the session to provide
		List<Pairing> awaiting = pairs.stream().filter(p -> isPairingOrphanAndNotPrioritized(p, period, reader.getPriority(date))).collect(Collectors.toList());
		if(awaiting.size() > 0) {
			System.out.println("Awaiting on "+date+" "+period.getMeridian()+" ["+awaiting.size()+"]: " + awaiting);
			pairs.removeAll(awaiting);
			orphanPairings.get(awaiting.get(0).getSoloStudent().practice).put(DatedSession.of(date, period), awaiting);
		}

		// Evaluate if we can expand
		CapacityScenario scenario = CapacityScenario.of(chairCapacity, pairs, capacity);
		List<Pairing> expandedPairs = scenario.getPairs();
		//printBeforeAfterPairings(pairs, expandedPairs);
		
		// Assign awaiting assistants
		/*
		if(awaiting.size() > 0) {
			List<Pairing> solo = pairs.stream().filter(Pairing::isSoloPairing).collect(Collectors.toList());
			if(solo.size() == 0) {
				info("No one to pair with");
			} else {
				info("Found "+solo.size()+" solo pairs; "+awaiting.size()+" orphans awaiting to assign.");
				List<Pairing> newPairs = new ArrayList<Pairing>(pairs);
				Collections.shuffle(solo, XOGridUtils.RANDOM);
				for(Pairing await : awaiting) {
					if(solo.size() == 0) {
						info("No solos remaining");
						continue;
					}
					Pairing p = solo.remove(0);
					if(!newPairs.remove(p)) {
						throw new RuntimeException("Something is broken here.");
					}
					
					info("Assigned " + await + " to " + p);
					newPairs.add(p.pair(await));
				}
				
				if(newPairs.size() != 0) {
					newPairs.forEach(System.out::println);
				}
			}
		}
		*/
		
		return expandedPairs;
	}

	private boolean isPairingOrphanAndNotPrioritized(Pairing p, Period period, UpperLower priority) {
		return p.type == PairingType.ORPHAN && ChairScheduler.selectProvider(p, period, priority) == Student.PLACEHOLDER;
	}

	private Predicate<Student> getOnlyAvailableStudentsSelector(XOGridReader reader, LocalDate _date, Period period) {
		GenericSession session = GenericSession.toSession(_date, period);
		return s -> {
			if((!s.isID3() && s.iso == session) || (s.isID3() && SODDateUtils.dateIsOnOrAfter(_date, reader.getID3ISOStart()) && s.iso == session)) {
				return false;
			} else if(reader.inHuddleDate(s.practice, _date, period)) {
				return false;
			} else if(reader.inLecture(s, _date, period)) {
				return false;
			} else if((s.isD3() && reader.inD3Perio(s, _date, period)) || (s.isID3() && reader.inID3Perio(s, _date, period))) {				
				return false;
			} else if((s.isD4() || s.isID4()) && SODDateUtils.dateIsOnOrAfter(_date, reader.getD4EarliestExit())) {
				return false;
			}
			
			return true;
		};
	}
	
	private Function<CapacityScenario, ScenarioMode> getScenarioSelectorDomestic(XOGridReader reader, LocalDate _date, Period period, int chairCapacity) {
		return scenario -> {
			if(reader.isClinicBreak(_date)) {
				return ScenarioMode.CLINIC_BREAK;
			} else if(reader.isD4LectureDate(_date) && period.isAM() || _date.getDayOfWeek() == DayOfWeek.FRIDAY && period.isPM()) {
				return ScenarioMode.REDUCED_CAPCITY;
			} else if(scenario.getChairsOccupied() < chairCapacity) {
				return ScenarioMode.BELOW_CAPCITY;
			} else {
				return ScenarioMode.SUFFICIENT;
			}
		};
	}

	private Function<CapacityScenario, ScenarioMode> getScenarioSelectorInternational(XOGridReader reader, LocalDate _date, Period period, int chairCapacity) {
		return scenario -> {
			if(reader.isClinicBreak(_date)) {
				return ScenarioMode.CLINIC_BREAK;
			} else if(reader.isID3LectureDate(_date, period) || reader.isD3LectureDate(_date) && period.isPM() || reader.isID4LectureDate(_date) && period.isAM()) {
				return ScenarioMode.MAXIMIZE;
			} else if(reader.isID4LectureDate(_date) && period.isAM()) {
				return ScenarioMode.REDUCED_CAPCITY;
			} else if(scenario.getChairsOccupied() < chairCapacity) {
				return ScenarioMode.BELOW_CAPCITY;
			} else {
				return ScenarioMode.SUFFICIENT;
			}
		};
	}
	
	private Pair<Student, String> getProviderAndLabel(Pairing p, GroupPractice practice, DatedSession date, UpperLower priority) {

		Student provider;
		String chairLabel;

		if(ERNPEScheduler.ER_LABEL.equals(p.label)) {
			provider = erRotation.getOrDefault(date, Map.of()).get(practice);
			chairLabel = ERNPEScheduler.ER_LABEL;
			if(provider == null) {
				throw new RuntimeException("ER provider expected for GP- "+practice+" on " + date);
			}
		} else if(ERNPEScheduler.NPE_LABEL.equals(p.label)) {
			provider = npvRotation.getOrDefault(date, Map.of()).get(practice);
			chairLabel = ERNPEScheduler.NPE_LABEL;
			if(provider == null) {
				throw new RuntimeException("NPE provider expected for GP- "+practice+" on " + date);
			}
		} else {
			provider = ChairScheduler.selectProvider(p, date.getPeriod(), priority);
			if(provider == Student.PLACEHOLDER) {
				return null;
			}
			
			//chairLabel = provider.cluster.notation + "_" + Character.toString(provider.pod);
			chairLabel = "";
		}
		
		return Pair.of(provider, chairLabel);
	}
	
	public static Comparator<Pairing> d2Priority = new Comparator<Pairing>() {
		@Override
		public int compare(Pairing o1, Pairing o2) {
			switch(o1.type) {
			case SECOND_32:
				if(o2.type == PairingType.SECOND_42) {
					return 0;
				} else {
					return -1;
				}
			case SECOND_42:
				if(o2.type == PairingType.SECOND_42) {
					return 1;
				} else if(o2.type == PairingType.SECOND_32) {
					return 0;
				} else {
					return -1;
				}
			default:
				if(o2.type == PairingType.SECOND_42 || o2.type == PairingType.SECOND_32) {
					return 1;
				} else {
					return 0;
				}
			}
		}
	};
	
	public static Comparator<Pairing> ER_NPE_Priority = new Comparator<Pairing>() {
		@Override
		public int compare(Pairing o1, Pairing o2) {
			String label = o1.label;
			if(label != null && (label.equals(ERNPEScheduler.ER_LABEL) || label.equals(ERNPEScheduler.NPE_LABEL))) {
				label = o2.label;
				if(label != null && (label.equals(ERNPEScheduler.ER_LABEL) || label.equals(ERNPEScheduler.NPE_LABEL))) {
					return 0;
				} else {
					return -1;
				}
			}
			
			label = o2.label;
			if(label != null && (label.equals(ERNPEScheduler.ER_LABEL) || label.equals(ERNPEScheduler.NPE_LABEL))) {
				return 1;
			} else {
				return 0;
			}
		}
	};
	
	public Comparator<Pairing> d2PriorityBalanced = d2Priority.thenComparing((p1, p2) -> {
		if(p1.type == PairingType.SECOND_42 || p1.type == PairingType.SECOND_32) {
			
			Student p1d2;
			if(p1.a.isD2()) {
				p1d2 = p1.a;
			} else if(p1.b.isD2()) {
				p1d2 = p1.b;
			} else {
				throw new RuntimeException("D2 not found: " + p1);
			}
			int p1Count = d2ProviderCount.getOrDefault(p1d2, List.of()).size();
			
			Student p2d2;
			if(p2.a.isD2()) {
				p2d2 = p2.a;
			} else if(p2.b.isD2()) {
				p2d2 = p2.b;
			} else {
				throw new RuntimeException("D2 not found: " + p1);
			}
			int p2Count = d2ProviderCount.getOrDefault(p1d2, List.of()).size();
			
			return Integer.compare(p1Count, p2Count);
			
		} else if(p1.a.isD2() || p1.b.isD2() || p2.a.isD2() || p2.b.isD2()) {
			throw new RuntimeException("D2 encountered for unexpected type: " + p1 + "; " + p2);
		} else {
			return 0;
		}
	});
}
