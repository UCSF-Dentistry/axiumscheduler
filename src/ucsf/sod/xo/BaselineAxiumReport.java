package ucsf.sod.xo;

import static ucsf.sod.xo.ChairScheduler.PAIRINGTYPES_OF_INTEREST;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import ucsf.sod.objects.DatedSession;
import ucsf.sod.objects.GenericPeriod;
import ucsf.sod.objects.GenericSession;
import ucsf.sod.util.SODDateUtils;
import ucsf.sod.util.SODExcelFactory;
import ucsf.sod.xo.ChairScheduler.ChairAssignment;
import ucsf.sod.xo.ChairScheduler.ChairMapper;
import ucsf.sod.xo.ChairScheduler.ChairPosition;
import ucsf.sod.xo.ChairScheduler.WeekSchedule;
import ucsf.sod.xo.XOGridUtils.ClinicStatus;
import ucsf.sod.xo.XOGridUtils.ClinicStatus.OpenMode;
import ucsf.sod.xo.calendar.AcademicCalendar;
import ucsf.sod.xo.calendar.AcademicCalendar.Quarter;
import ucsf.sod.xo.objects.GroupPractice;
import ucsf.sod.xo.objects.PerioSession;
import ucsf.sod.xo.objects.Practice;
import ucsf.sod.xo.objects.Student;
import ucsf.sod.xo.report.Column;
import ucsf.sod.xo.report.DoubleMappedColumn;
import ucsf.sod.xo.report.MapReport;
import ucsf.sod.xo.report.MappedColumn;
import ucsf.sod.xo.report.MappedColumnGeneric;
import ucsf.sod.xo.scheduler.ERNPEScheduler;
import ucsf.sod.xo.scheduler.Pairing;
import ucsf.sod.xo.scheduler.PairingType;

public enum BaselineAxiumReport {

	INSTANCE;

	public static SODExcelFactory generate(
		Map<DatedSession, Map<GroupPractice, List<Pairing>>> dailyWorkforce,
		Map<DatedSession, Map<GroupPractice, Map<ChairPosition, ChairAssignment>>> dailyLayout,
		Map<DatedSession, List<Pairing>> dailyPerioWorkforce,
		Map<DatedSession, Map<ChairPosition, ChairAssignment>> dailyPerioLayout,
		XOGridReader reader,
		Map<DatedSession, Map<GroupPractice, Student>> npvRotation,
		Map<DatedSession, Map<GroupPractice, Student>> erRotation,
		Map<GroupPractice, Map<DatedSession, List<Pairing>>> orphanPairings
	) {
		SODExcelFactory factory = new SODExcelFactory();
		BaselineAxiumReport.generateGlobalStatistics(factory, dailyWorkforce);
		
		generateStudentAllocationStatistics(
			factory,
			"Perio_Distribution",
			dailyPerioLayout.entrySet().stream()
				.flatMap(e -> e.getValue().entrySet().stream()) // every chair position
				.map(e -> e.getValue()) 						// get the chair assignment
				.filter(c -> c != null)
				.collect(Collectors.groupingBy(
					ChairAssignment::getAssigned,
					TreeMap::new,
					Collectors.toList()
			))
		);
		
		generateChairAllocation(
			factory, 
			"Perio",
			session -> ChairMapper.ofIterativeFull(ChairScheduler.perioChairMapper),
			dailyPerioLayout.entrySet().iterator(),
			Practice.PERIO
		);

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

		generateStudentAllocationStatistics(
				factory,
				"Predoc_Distribution",
				assignmentsByStudent
			);
		

		generateNPESummary(factory, dailyLayout);
		generateERSummary(factory, dailyLayout);

		generateISO(
			factory,
			"ID4 ISO",
			GroupPractice.F.getAllStudents().stream().filter(s -> s.isID4()).collect(Collectors.toList()),
			reader.getAcademicCalendar(),
			reader.getAcademicCalendar().getAcademicStartDate().minusWeeks(2),
			SODDateUtils.ceilingToDayOfWeek(reader.getD4EarliestExit(), DayOfWeek.FRIDAY)
		);

		{
			AcademicCalendar calendar = reader.getAcademicCalendar();
			generateISO(
				factory,
				"ID3 ISO",
				Student.getStudentsSorted(Student::isID3),
				calendar,
				reader.getID3ISOStart(),
				calendar.getAcademicEndDate()
			);
		}

		generateLecture(factory, reader);
		generateHuddle(factory, reader);
		
		for(GroupPractice practice : XOGridUtils.ALL_PRACTICES) {
			generateChairAllocation(
				factory, 
				"GP-" + practice,
				session -> {
					if(session.getDayOfWeek() == DayOfWeek.FRIDAY && session.isPM()) {
						ChairMapper friMapper;
						Quarter q = AcademicCalendar.CURRENT_YEAR.toQuarter(session.date);
						switch(q) {
						case SUMMER:
							friMapper = ChairMapper.ofIterativeFull(ChairScheduler.friPMChairMapperSummer.get(practice)); break;
						case FALL:
						case WINTER:
							friMapper = ChairMapper.ofIterativeFull(ChairScheduler.friPMChairMapper.get(practice)); break;
						case SPRING:
							friMapper = ChairMapper.ofIterativeFull(ChairScheduler.chairMapper.get(practice)); break;
						default:
							throw new RuntimeException("Unexpected quarter for ["+session.date+"]: " + q);
						}
						
						return friMapper;
					} else {
						return ChairMapper.ofIterativeFull(ChairScheduler.chairMapper.get(practice));
					}
				},
				dailyLayout.entrySet().stream()
					.collect(Collectors.mapping(e -> Map.entry(e.getKey(), e.getValue().getOrDefault(practice, Map.of())), Collectors.toList())).iterator(),
				Practice.PARNASSUS
			);
			
			generateListOrphans(factory, practice, orphanPairings.get(practice));
		}

		
		SODExcelFactory studentScheduleOverView = factory;
		for(Student s : Student.getStudentsSorted(s -> s.isD3() || s.isD4() || s.isID3() || s.isID4() || s.isD2())) {
			System.err.println("Building schedule for " + s.id);

			Map<LocalDate, WeekSchedule> schedule = assignmentsByStudent.getOrDefault(s, List.of()).stream().collect(
				Collectors.groupingBy(
					a -> SODDateUtils.floorToLastMonday(a.session.date),
					() -> new TreeMap<LocalDate, WeekSchedule>(),
					Collectors.collectingAndThen(
						Collectors.toList(),
						l -> WeekSchedule.of(l)
					)
				)
			);

			generateStudentScheduleSummary(studentScheduleOverView, s, schedule, reader, npvRotation, erRotation);
		}

		return factory;
	}
		
	public static void generateGlobalStatistics(SODExcelFactory parentWorkbook, Map<DatedSession, Map<GroupPractice, List<Pairing>>> dailyWorkforce) {

		new MapReport<DatedSession, Map<GroupPractice, List<Pairing>>>(
			"global",
			List.of(
				Column.<Entry<DatedSession, Map<GroupPractice, List<Pairing>>>>of("Session", s -> s.getKey().toStringPretty()),
				MappedColumn.<Entry<DatedSession, Map<GroupPractice, List<Pairing>>>, GroupPractice, List<Pairing>>of(
					Entry::getValue,
					XOGridUtils.ALL_PRACTICES,
					l -> Integer.toString(l.size()),
					List.of()
				),
				MappedColumn.<Entry<DatedSession, Map<GroupPractice, List<Pairing>>>, PairingType, Long>of(
					e -> e.getValue().entrySet().stream().flatMap(_e -> _e.getValue().stream()).collect(Collectors.groupingBy(p -> p.type, () -> new TreeMap<PairingType, Long>(), Collectors.counting())),
					List.of(
						PairingType.PRIMARY,
						PairingType.SECONDARY_4,
						PairingType.SECONDARY_3,
						PairingType.SECOND_42,
						PairingType.SECOND_32,
						PairingType.SEPARATED,
						PairingType.ORPHAN
					),
					l -> Integer.toString(l.intValue()),
					0L
				)
			)
		).generate(parentWorkbook, dailyWorkforce.entrySet().iterator());
	}
	
	public static void generateStudentAllocationStatistics(SODExcelFactory factory, String name, Map<Student, List<ChairAssignment>> assignments) {
		
		new MapReport<Student, List<ChairAssignment>>(
			name,
			List.of(
				// Student Info
				MappedColumnGeneric.<Entry<Student, List<ChairAssignment>>, Student>of(
					List.of(
						"Student ID",
						"First",
						"Last",
						"GP",
						"ISO"
					),
					Entry::getKey,
					List.of(
						s -> s.id,
						s -> s.first,
						s -> s.last,
						s -> s.practice.toString(), 
						s -> s.iso.toStringPretty()
					)
				),
				
				// Daily Values
				DoubleMappedColumn.<Entry<Student, List<ChairAssignment>>, Pair<DayOfWeek, String>, PairingType, Long>of(
					e -> e.getValue().stream().collect(
						Collectors.groupingBy(
							assignment -> Pair.of(assignment.session.date.getDayOfWeek(), assignment.session.session.getPeriod().isAM() ? "AM" : "PM"),
							Collectors.groupingBy(
								_a -> _a.pairing.type,
								Collectors.counting()
							)
					)),
					DAYS_OF_WEEK,
					List.of(
						PairingType.PRIMARY,
						PairingType.SECONDARY_4,
						PairingType.SECONDARY_3,
						PairingType.SEPARATED,
						PairingType.ORPHAN,
						PairingType.SECOND_42,
						PairingType.SECOND_32
					),
					l -> Integer.toString(l.intValue()),
					0L
				),
				
				// Total number of pairs
				Column.<Entry<Student, List<ChairAssignment>>>of(
					"Total", 
					e -> Integer.toString(e.getValue().size())
				),

				Column.emptyColumn(),
				
				// Partner Info
				MappedColumnGeneric.<Entry<Student, List<ChairAssignment>>, Student>of(
					List.of(
						"Partner ID",
						"Total"
					),
					e -> e.getKey().getPartner(),
					List.of(
						partner -> partner != Student.PLACEHOLDER ? partner.id : "",
						partner -> partner != Student.PLACEHOLDER ? Integer.toString(assignments.get(partner).size()) : ""
					)
				)
			)
		) {
			@Override
			protected void generateHeader(SODExcelFactory sheet) {
				sheet.createRow();
				sheet.blankCell(5);
				for(Pair<DayOfWeek, String> p : DAYS_OF_WEEK) {
					sheet.createCell(p.getLeft() + " " + p.getRight()).blankCell(6);
					var asdf = sheet.getCurrentPosition().getRight();
					sheet.mergeCellsCurrentRow(asdf-PAIRINGTYPES_OF_INTEREST.size(), asdf-1);
				}
				super.generateHeader(sheet);
			}
		}.generate(factory, assignments.entrySet().iterator());
		
		int limit = (PAIRINGTYPES_OF_INTEREST.size() * 10) + 4;
		for(int i = 0; i < limit; i++) {
			factory.autofitColumn(i);
		}
		factory.setSheetToFront();
	}
	
	public static final List<Pair<DayOfWeek, String>> DAYS_OF_WEEK = List.of(
		Pair.of(DayOfWeek.MONDAY, "AM"),
		Pair.of(DayOfWeek.MONDAY, "PM"),
		Pair.of(DayOfWeek.TUESDAY, "AM"),
		Pair.of(DayOfWeek.TUESDAY, "PM"),
		Pair.of(DayOfWeek.WEDNESDAY, "AM"),
		Pair.of(DayOfWeek.WEDNESDAY, "PM"),
		Pair.of(DayOfWeek.THURSDAY, "AM"),
		Pair.of(DayOfWeek.THURSDAY, "PM"),
		Pair.of(DayOfWeek.FRIDAY, "AM"),
		Pair.of(DayOfWeek.FRIDAY, "PM")
	);
	
	private static void generateStudentScheduleSummary(SODExcelFactory parentWorkbook, Student s, Map<LocalDate, WeekSchedule> schedule, XOGridReader reader, Map<DatedSession, Map<GroupPractice, Student>> npvRotation, Map<DatedSession, Map<GroupPractice, Student>> erRotation) {
		
		SODExcelFactory factory = parentWorkbook.createSheet(s.id);
		
		factory.createRow();
		factory.blankCell();
		for(Pair<DayOfWeek, String> p : DAYS_OF_WEEK) {
			factory.createCell(p.getLeft().getDisplayName(TextStyle.SHORT, Locale.US) + " " + p.getRight());
		}
		factory.blankCell();
		for(Pair<DayOfWeek, String> p : DAYS_OF_WEEK) {
			factory.createCell(p.getLeft().getDisplayName(TextStyle.SHORT, Locale.US) + " " + p.getRight());
		}

		LocalDate end = reader.getLastDate();
		for(LocalDate date = SODDateUtils.floorToLastMonday(reader.getFirstDate()) ; SODDateUtils.dateIsOnOrBefore(date, end); date = date.plusWeeks(1)) {
			factory.createRow();
			factory.createCell(date);

			WeekSchedule ws = schedule.get(date);
			if(ws == null) {
				factory.createCell(reader.getRotationByStudent(s, date, null));
				factory.mergeCellsCurrentRow(1, 10);
			} else {
				
				List<String> types = new ArrayList<String>();
				types.add("");
				
				int offset = 0;
				for(ChairAssignment a : new ChairAssignment[] {
					ws.monAM, ws.monPM,
					ws.tueAM, ws.tuePM,
					ws.wedAM, ws.wedPM,
					ws.thuAM, ws.thuPM,
					ws.friAM, ws.friPM,
				}) {
					if(a == null) {
						if(s.iso == GenericSession.toSession(DayOfWeek.of((offset >> 1)+1), offset % 2 == 0 ? GenericPeriod.GENERIC_AM : GenericPeriod.GENERIC_PM)) {
							factory.createCell("ISO");
						} else {
							factory.blankCell();
						}
						types.add("");
					} else if(erRotation.getOrDefault(a.session, Map.of()).getOrDefault(s.practice, Student.PLACEHOLDER) == s) {
						factory.createCell("ER");
						types.add(Character.toString(a.pairing.type.symbol));
					} else if(npvRotation.getOrDefault(a.session, Map.of()).getOrDefault(s.practice, Student.PLACEHOLDER) == s) {
						factory.createCell("NPE");
						types.add(Character.toString(a.pairing.type.symbol));
					} else {
						factory.createCell(1);
						types.add(Character.toString(a.pairing.type.symbol));
					}
					offset++;
				}
				
				for(String t : types) {
					factory.createCell(t);
				}
			}
		}
		//factory.setSheetToFront();
		for(int i = 0; i < 12; i++) {
			factory.autofitColumn(i);
		}
	}
	
	public static void generateERSummary(SODExcelFactory parentWorkbook, Map<DatedSession, Map<GroupPractice, Map<ChairPosition, ChairAssignment>>> dailyLayout) {
		Map<Student, List<ChairAssignment>> stats = dailyLayout.entrySet().stream()
			.flatMap(e -> e.getValue().entrySet().stream()) // every group practice 
			.flatMap(e -> e.getValue().entrySet().stream()) // every chair position
			.map(e -> e.getValue()) 						// get the chair assignment
			.filter(c -> c != null && c.pairing.label != null && c.pairing.label.equals(ERNPEScheduler.ER_LABEL))
			.collect(Collectors.groupingBy(
				c -> c.getAssigned(),
				TreeMap::new,
				Collectors.toList()
			));
		SODExcelFactory erCounts = parentWorkbook.createSheet("ER Distribution");
		erCounts.createRow();
		erCounts.fillCellAndMerge("Student", 3);
		erCounts.createCell("Practice");
		erCounts.createCell("Count");
		erCounts.createCell("Dates");
		
		for(Student s : stats.keySet()) {
			erCounts.createRow();
			erCounts
				.createCell(s.id)
				.createCell(s.first)
				.createCell(s.last)
				.createCell(s.practice);
			
			List<ChairAssignment> l = stats.get(s);
			
			erCounts.createCell(l.size());
			
			l.forEach(c -> erCounts.createCell(c.session.toStringPretty()));
		}
		
		Map<DatedSession, Map<GroupPractice, ChairAssignment>> assignment = dailyLayout.entrySet().stream() 					// every session
			.flatMap(e -> e.getValue().entrySet().stream()) // every group practice 
			.flatMap(e -> e.getValue().entrySet().stream()) // every chair position
			.map(e -> e.getValue()) 						// get the chair assignment
			.filter(c -> c != null && c.pairing.label != null && c.pairing.label.equals(ERNPEScheduler.ER_LABEL))
			.collect(Collectors.groupingBy(
				c -> c.session,
				TreeMap::new,
				Collectors.toMap(c -> c.getAssigned().practice, Function.identity(), (c1, c2) -> { throw new RuntimeException("Collision found"); }, XOGridUtils::createGroupPracticeEnumMap)
			));
		generateERNPEAllocation(
			parentWorkbook,
			"ER Daily Assignment",
			assignment
		);

		SODExcelFactory er = parentWorkbook.createSheet("ER");
		for(DatedSession session : assignment.keySet()) {
			Map<GroupPractice, ChairAssignment> m = assignment.get(session);

			String startTime;
			String endTime;
			if(session.isAM()) {
				startTime = "08:10";
				endTime = "08:30";
			} else if(session.isPM()) {
				startTime = "12:40";
				endTime = "13:00";				
			} else {
				throw new RuntimeException("Session is neither AM or PM: " + session);
			}
			
			for(GroupPractice p : m.keySet()) {
				er.createRow(List.of(
					ChairScheduler.DATE_FORMAT.format(session.date),
					startTime,
					endTime,
					m.get(p).getAssigned().id,
					"ER Rotation"				
				));
			}
		}
	}
	
	public static void generateNPESummary(SODExcelFactory parentWorkbook, Map<DatedSession, Map<GroupPractice, Map<ChairPosition, ChairAssignment>>> dailyLayout) {
		Map<Student, List<ChairAssignment>> stats = dailyLayout.entrySet().stream()
			.flatMap(e -> e.getValue().entrySet().stream()) // every group practice 
			.flatMap(e -> e.getValue().entrySet().stream()) // every chair position
			.map(e -> e.getValue()) 						// get the chair assignment
			.filter(c -> c != null && c.pairing.label != null && c.pairing.label.equals(ERNPEScheduler.NPE_LABEL))
			.collect(Collectors.groupingBy(
				c -> c.getAssigned(),
				TreeMap::new,
				Collectors.toList()
			));
		SODExcelFactory npeCounts = parentWorkbook.createSheet("NPE Distribution");
		npeCounts.createRow();
		npeCounts.fillCellAndMerge("Student", 3);
		npeCounts.createCell("Practice");
		npeCounts.createCell("Count");
		npeCounts.createCell("Dates");
		
		for(Student s : stats.keySet()) {
			npeCounts.createRow();
			npeCounts
				.createCell(s.id)
				.createCell(s.first)
				.createCell(s.last)
				.createCell(s.practice);
			
			List<ChairAssignment> l = stats.get(s);
			
			npeCounts.createCell(l.size());
			
			l.forEach(c -> npeCounts.createCell(c.session.toStringPretty()));
		}
		
		Map<DatedSession, Map<GroupPractice, ChairAssignment>> assignment = dailyLayout.entrySet().stream() 					// every session
			.flatMap(e -> e.getValue().entrySet().stream()) // every group practice 
			.flatMap(e -> e.getValue().entrySet().stream()) // every chair position
			.map(e -> e.getValue()) 						// get the chair assignment
			.filter(c -> c != null && c.pairing.label != null && c.pairing.label.equals(ERNPEScheduler.NPE_LABEL))
			.collect(Collectors.groupingBy(
				c -> c.session,
				TreeMap::new,
				Collectors.toMap(c -> c.getAssigned().practice, Function.identity(), (c1, c2) -> { throw new RuntimeException("Collision found"); }, XOGridUtils::createGroupPracticeEnumMap)
			));
		generateERNPEAllocation(
			parentWorkbook,
			"NPE Daily Assignment",
			assignment
		);

		SODExcelFactory npe = parentWorkbook.createSheet("NPE");
		for(DatedSession session : assignment.keySet()) {
			Map<GroupPractice, ChairAssignment> m = assignment.get(session);

			String startTime;
			String endTime;
			if(session.isAM()) {
				startTime = "08:10";
				endTime = "08:30";
			} else if(session.isPM()) {
				startTime = "12:40";
				endTime = "13:00";				
			} else {
				throw new RuntimeException("Session is neither AM or PM: " + session);
			}
			
			for(GroupPractice p : m.keySet()) {
				npe.createRow(List.of(
					ChairScheduler.DATE_FORMAT.format(session.date),
					startTime,
					endTime,
					m.get(p).getAssigned().id,
					"NPE"				
				));
			}
		}
	}
	
	public static void generateERNPEAllocation(SODExcelFactory factory, String sheetName, Map<DatedSession, Map<GroupPractice, ChairAssignment>> assignment) {
		SODExcelFactory ER = factory.createSheet(sheetName);

		// Generate Header
		ER.createRow();
		ER.fillCellAndMerge("Session", 2);
		for(GroupPractice p : XOGridUtils.ALL_PRACTICES) {
			ER.createCell("GP-" + p).blankCell(2);
			var asdf = ER.getCurrentPosition().getRight();
			ER.mergeCellsCurrentRow(asdf-3, asdf-1);
		}
		
		for(DatedSession session : new TreeSet<DatedSession>(assignment.keySet())) {
			Map<GroupPractice, ChairAssignment> m = assignment.get(session);
			
			ER.createRow();
			ER.createCell(session.getDayOfWeek());
			ER.createCell(session.toStringPretty());
			
			for(GroupPractice p : XOGridUtils.ALL_PRACTICES) {
				ChairAssignment a = m.get(p);
				if(a == null) {
					ER.createCell("");
					ER.createCell("");
					ER.createCell("");
				} else {
					Student assigned = a.getAssigned();
					ER.createCell(assigned.id);
					ER.createCell(assigned.first);
					ER.createCell(a.pairing.type);
				}
			}
		}
	}	

	private static Set<DatedSession> assignedER = new TreeSet<DatedSession>();
	
	public static void generateChairAllocation(SODExcelFactory factory, String sheetName, Function<DatedSession, ChairMapper> supplier, Iterator<Map.Entry<DatedSession, Map<ChairPosition, ChairAssignment>>> iter, Practice practice) {

		SODExcelFactory out = factory.createSheet(sheetName);
		SODExcelFactory assist = factory.createSheet(sheetName + "-ASSIST");
		while(iter.hasNext()) {
			
			DatedSession session;
			Map<ChairPosition, ChairAssignment> layout;
			{
				var p = iter.next();
				session = p.getKey();
				layout = p.getValue();
			}
			boolean isAM = session.isAM();
			ChairMapper mapper = supplier.apply(session);

			for(ChairPosition position : layout.keySet()) {

				ChairAssignment assign = layout.get(position);
				if(assign == null) {
					continue;
				}
				Student provider = assign.getAssigned();
				String chairLabel = assign.pairing.label;
				if(chairLabel == null) {
					chairLabel = "";
				}

				switch(practice) {
					case PERIO:
						if(isAM) {
							out.createRow(ChairScheduler.buildChairAssignment(session.date, "AM", mapper.get(position), provider, chairLabel, "08:30", "12:00"));
						} else {
							out.createRow(ChairScheduler.buildChairAssignment(session.date, "PM", mapper.get(position), provider, chairLabel, "13:30", "17:00"));
						}
						break;
					case PARNASSUS:
						boolean erOverride = ERNPEScheduler.ER_LABEL.equals(chairLabel);
						String chairName;
						if(erOverride) {
							if(session.getDayOfWeek() == DayOfWeek.FRIDAY && session.isPM()) {
								Quarter q = AcademicCalendar.CURRENT_YEAR.toQuarter(session.date);
								switch(q) {
								case SUMMER:
									chairName = mapper.get(position); break;
								case FALL:
								case WINTER:
									chairName = assignedER.contains(session) ? "B17" : "B18"; break;
								case SPRING:
									chairName = assignedER.contains(session) ? "B8" : "B1"; break;
								default:
									throw new RuntimeException("Unexpected quarter for ["+session+"]: " + q);
								}	
							} else {
								chairName = assignedER.contains(session) ? "B8" : "B1";
							}
							assignedER.add(session);
						} else {
							chairName = mapper.get(position);
						}
						
						if(isAM) {
							out.createRow(ChairScheduler.buildChairAssignment(session.date, "AM", chairName, provider, chairLabel, "08:30", "11:30"));
						} else {
							out.createRow(ChairScheduler.buildChairAssignment(session.date, "PM1", chairName, provider, chairLabel, "13:00", "15:00"));
							out.createRow(ChairScheduler.buildChairAssignment(session.date, "PM2", chairName, provider, chairLabel, "15:00", "17:00"));
						}
						break;
					default:
						throw new RuntimeException("Unexpected practice: " + practice);
				}
				
				Student assistant = assign.pairing.a;
				if(assistant == provider) {
					assistant = assign.pairing.b;
				}
				
				if(assistant != Student.PLACEHOLDER) {
					
					String startTime;
					String endTime;
					if(session.isAM()) {
						startTime = "08:30";
						endTime = "12:00";
					} else if(session.isPM()) {
						startTime = "13:00";
						endTime = "17:00";				
					} else {
						throw new RuntimeException("Session is neither AM or PM: " + session);
					}

					assist.createRow(List.of(
							ChairScheduler.DATE_FORMAT.format(session.date),
							startTime,
							endTime,
							assistant.id,
							"Assisting"				
						));					
				}
			}
		}
	}
	
	public static void generateISO(SODExcelFactory parentWorkbook, String name, Collection<Student> students, AcademicCalendar calendar, LocalDate startDate, LocalDate endDate) {

		SODExcelFactory id4iso = parentWorkbook.createSheet(name);
		for(LocalDate date = startDate; SODDateUtils.dateIsOnOrBefore(date, endDate); date = date.plusDays(date.getDayOfWeek() == DayOfWeek.FRIDAY ? 3 : 1)) {

			ClinicStatus status = calendar.get(date);
			if(status.status == OpenMode.CLOSED) {
				continue;
			}

			for(GenericPeriod period : List.of(GenericPeriod.GENERIC_AM, GenericPeriod.GENERIC_PM)) {
				GenericSession session = GenericSession.toSession(date, period);

				String startTime;
				String endTime;
				if(session.isAM()) {
					startTime = "08:30";
					endTime = "11:30";
				} else if(session.isPM()) {
					startTime = "13:00";
					endTime = "17:00";				
				} else {
					throw new RuntimeException("Session is neither AM or PM: " + session);
				}

				for(Student s : students) {
					if(s.iso == session) {
						id4iso.createRow(List.of(
							ChairScheduler.DATE_FORMAT.format(date),
							startTime,
							endTime,
							s.id,
							"ISO/CI Time"				
						));
					}
				}
			}
		}
	}
	
	public static void generateLecture(SODExcelFactory factory, XOGridReader reader) {
		
		SODExcelFactory lecture = factory.createSheet("Lecture");
		SODExcelFactory lectureDates = factory.createSheet("Lecture Dates");
		
		lectureDates.createRow("I/D4 Lecture");
		for(LocalDate session : reader.getD4LectureDates()) {
			lectureDates.createRow(List.of("", session.toString()));
			for(Student s : Student.getStudentsSorted(s -> s.isD4() || s.isID4())) {
				lecture.createRow(List.of(
					ChairScheduler.DATE_FORMAT.format(session),
					"09:00",
					"12:00",
					s.id,
					"Lecture (9:00-12:00)"				
				));
			}
		}
		
		lectureDates.createRow();
		
		lectureDates.createRow("I/D3 Lecture");
		for(LocalDate session : reader.getD3LectureDates()) {
			lectureDates.createRow(List.of("", session.toString()));
			for(Student s : Student.getStudentsSorted(s -> s.isD3() || s.isID3())) {
				lecture.createRow(List.of(
					ChairScheduler.DATE_FORMAT.format(session),
					"13:00",
					"17:00",
					s.id,
					"Lecture"				
				));
			}
		}
		
		LocalDate start = LocalDate.of(reader.getAcademicCalendar().startYear, Month.JULY, 10);
		Map<PerioSession, List<Student>> d3s = Student.getStudentsSorted().stream().filter(Student::isD3).collect(Collectors.groupingBy(s -> s.d3perio));
		for(PerioSession session : d3s.keySet()) {

			String startTime;
			String endTime;
			if(session.isAM()) {
				startTime = "08:30";
				endTime = "12:00";
			} else if(session.isPM()) {
				startTime = "13:30";
				endTime = "17:00";				
			} else {
				throw new RuntimeException("Session is neither AM or PM: " + session);
			}

			LocalDate date = SODDateUtils.ceilingToDayOfWeek(start, session.day);
			
			for(Student s : d3s.get(session)) {
				lecture.createRow(List.of(
					ChairScheduler.DATE_FORMAT.format(date),
					startTime,
					endTime,
					s.id,
					"Perio Seminar"				
				));
			}
		}
	}
	
	public static void generateHuddle(SODExcelFactory factory, XOGridReader reader) {
		SODExcelFactory huddle = factory.createSheet("Huddle");
		SODExcelFactory huddleByPractice = factory.createSheet("Huddle Dates");
		Map<GroupPractice, List<LocalDate>> huddles = reader.getHuddleDatesAll();

		for(GroupPractice p : XOGridUtils.ALL_PRACTICES) {
			huddleByPractice.createRow("GP-" + p);
			for(LocalDate d : huddles.get(p)) {
				huddleByPractice.createCell(d);
				for(Student s : p.getClinicStudents()) {
					huddle.createRow(List.of(
						ChairScheduler.DATE_FORMAT.format(d),
						"08:30",
						"11:30",
						s.id,
						"HUDDLE"				
					));
				}
			}
		}
		
	}
	
	public static void generateListOrphans(SODExcelFactory factory, GroupPractice practice, Map<DatedSession, List<Pairing>> orphans) {
		SODExcelFactory huddle = factory.createSheet("Available GP-"+practice);
		for(DatedSession date : new TreeSet<DatedSession>(orphans.keySet())) {
			
			List<String> main = new ArrayList<String>();
			main.add(ChairScheduler.DATE_FORMAT.format(date.date));
			main.add(date.getMeridian());
			
			List<String> secondary = new ArrayList<String>();
			secondary.add("");
			secondary.add("");
			
			orphans.get(date).forEach(pair -> {
				main.add(pair.a.id);
				secondary.add(pair.b.id);
			});
			
			huddle.createRow(main);
			huddle.createRow(secondary);
		}
	}
}
