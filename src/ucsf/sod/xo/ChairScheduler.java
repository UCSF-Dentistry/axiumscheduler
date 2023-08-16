package ucsf.sod.xo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;

import ucsf.sod.objects.DatedSession;
import ucsf.sod.objects.Period;
import ucsf.sod.util.SODDateUtils;
import ucsf.sod.xo.calendar.AcademicCalendar;
import ucsf.sod.xo.objects.GroupPractice;
import ucsf.sod.xo.objects.Student;
import ucsf.sod.xo.objects.Student.UpperLower;
import ucsf.sod.xo.scheduler.ERNPEScheduler;
import ucsf.sod.xo.scheduler.Pairing;
import ucsf.sod.xo.scheduler.PairingType;

public enum ChairScheduler {

	DEFAULT {
		@Override
		public Map<ChairPosition, ChairAssignment> assignChairs(DatedSession date, List<Pairing> pairs, ChairMapper mapper, Function<Pairing, Pair<Student, String>> pairProcessor) {

			if(mapper.mode != ChairMapper.AccessMode.ITERATIVE) {
				throw new RuntimeException("ChairMapper needs to be set to Iterative Mode");
			}
			
			List<Pairing> orphans = new ArrayList<Pairing>();

			// Assign pairs to chairs
			Map<ChairPosition, ChairAssignment> floormap = new TreeMap<ChairPosition, ChairAssignment>();
			for(Pairing p : pairs) {
				
				if(mapper.hasNoChairsToMap()) {
					if(ERNPEScheduler.NPE_LABEL.equals(p.label)) {
						throw new RuntimeException("Chair needs to be scheduled on date " + date);
					}
					orphans.add(p);
					continue;
				}
				
				// Set the provider and the chair label
				Student provider;
				String chairLabel;
				{
					Pair<Student, String> tuple = pairProcessor.apply(p);
					if(tuple == null) {
						continue;
					}
					
					provider = tuple.getLeft();
					if(provider.isID3() && date.date.isBefore(LocalDate.of(2023, Month.OCTOBER, 16))) { // TODO: do this date lookup with the academic calendar -- or pull from X-O Grid Reader
						Student newProvider = p.a;
						if(provider == newProvider) {
							newProvider = p.b;
						}
						
						if(newProvider.isID3()) {
							System.err.println("ID3 ["+provider.id+"] cannot provide on " + date +" and other provider is also ID3: " + newProvider.id + ", skipping...");
							orphans.add(p);
							continue;
						} else if(newProvider == Student.PLACEHOLDER) {
							System.err.println("Other provider is a placeholder, skipping ID3 "+provider.id+" as a provider on " + date);
							orphans.add(p);
							continue;
						} else {
							System.err.println("Overriding ID3 ["+provider.id+"] on " + date + " with " + newProvider.id);
							provider = newProvider;
						}
						
						if(provider.isID3()) {
							throw new RuntimeException("Why here?!");
						}
					}

					chairLabel = tuple.getRight();
				}
				
				// Select the chair
				String lookup = "";
				if(provider.practice != GroupPractice.F) {
					lookup = provider.cluster.name().substring(1) + "_" + (provider.isUpperStudent() ? "U" : "L") + "_" + provider.pod;
				}

				ChairPosition chair;
				if(ERNPEScheduler.NPE_LABEL.equals(chairLabel)) {
					chair = mapper.getLastChair();
					if(chair != ChairPosition._99_L_1) {
						throw new RuntimeException("NPE chair cannot be found: " + chair);
					}
				} else if(ERNPEScheduler.ER_LABEL.equals(chairLabel)) {
					/*
					Quarter q = AcademicCalendar.CURRENT_YEAR.toQuarter(date.date);
					switch(q) {
					case SUMMER:
						chair = mapper.toChairPosition(lookup); break;
					case FALL:
					case WINTER:
					case SPRING:
						chair = ChairPosition.ER; break;
					default:
						throw new RuntimeException("Unexpected quarter for ["+date+"]: " + q);
					}
					*/
					chair = ChairPosition.ER;
				} else {
					chair = mapper.toChairPosition(lookup);
					if(floormap.get(chair) != null) { // If chair is already assigned, find another chair with whatever chairs are left
						//System.err.println("Chair " + mapper.get(chair) + " ["+lookup+"] is used more than once on " + date);
						orphans.add(p);
						continue;
					}
				}
				
				ChairAssignment assignment = new ChairAssignment(date, p, provider);
				floormap.put(chair, assignment);
			}

			if(orphans.size() > 0) {
				System.err.println("Unable to schedule " + orphans.size() + " of " +pairs.size()+ " pair(s) on " + date);
			}
			
			return floormap;
		}
	},
	LEGACY_AY2223 {
		@Override
		public Map<ChairPosition, ChairAssignment> assignChairs(DatedSession date, List<Pairing> pairs, ChairMapper mapper, Function<Pairing, Pair<Student, String>> pairProcessor) {

			if(mapper.mode != ChairMapper.AccessMode.ITERATIVE) {
				throw new RuntimeException("ChairMapper needs to be set to Iterative Mode");
			}
			
			List<Pairing> orphans = new ArrayList<Pairing>();

			// Assign pairs to chairs
			Map<ChairPosition, ChairAssignment> floormap = ChairPosition.getEmptyFloor();
			for(Pairing p : pairs) {
				
				if(mapper.hasNoChairsToMap()) {
					orphans.add(p);
					continue;
				}
				
				// Set the provider and the chair label
				Student provider;
				String chairLabel;
				{
					Pair<Student, String> tuple = pairProcessor.apply(p);
					if(tuple == null) {
						continue;
					}
					
					provider = tuple.getLeft();
					if(provider.practice == GroupPractice.F && provider.isID3() && date.date.isBefore(LocalDate.of(2022, Month.OCTOBER, 24))) { // TODO: do this date lookup with the academic calendar
						Student newProvider = p.a;
						if(provider == newProvider) {
							newProvider = p.b;
						}
						
						if(newProvider.isD3()) {
							System.err.println("Other provider is also ID3: " + p);
						} else if(newProvider == Student.PLACEHOLDER) {
							System.err.println("Other provider is a placeholder, skipping ID3 "+provider.id+" as a provider on " + date);
							continue;
						} else {
							provider = newProvider;
						}
					}

					chairLabel = tuple.getRight();
				}
				
				// Select the chair
				String lookup = "";
				if(provider.practice != GroupPractice.F) {
					lookup = provider.cluster.name().substring(1) + "_" + (provider.isUpperStudent() ? "U" : "L") + "_" + provider.pod;
				}
				ChairPosition chair = mapper.toChairPosition(lookup);
				if(floormap.get(chair) != null) { // If chair is already assigned, find another chair with whatever chairs are left
					//System.err.println("Chair " + mapper.get(chair) + " ["+lookup+"] is used more than once on " + date);
					orphans.add(p);
					continue;
				}
				
				ChairAssignment assignment = new ChairAssignment(date, p, provider);
				floormap.put(chair, assignment);
			}

			if(orphans.size() > 0) {
				System.err.println("Unable to schedule " + orphans.size() + " of " +pairs.size()+ " pair(s) on " + date);
			}
			
			return floormap;
		}
	},
	LEGACY {
		@Override
		public Map<ChairPosition, ChairAssignment> assignChairs(DatedSession date, List<Pairing> pairs, ChairMapper mapper, Function<Pairing, Pair<Student, String>> pairProcessor) {

			List<Pairing> orphans = new ArrayList<Pairing>();

			// Assign pairs to chairs
			Map<ChairPosition, ChairAssignment> floormap = ChairPosition.getEmptyFloor();
			for(Pairing p : pairs) {
				if(p.pod == '5') {
					orphans.add(p);
					continue;
				}
				
				// Set the provider and the chair label
				Student provider;
				String chairLabel;
				{
					Pair<Student, String> tuple = pairProcessor.apply(p);
					if(tuple == null) {
						continue;
					}
					
					provider = tuple.getLeft();
					if(provider.practice == GroupPractice.F && provider.isID3() && date.date.isBefore(LocalDate.of(2022, Month.OCTOBER, 24))) { // TODO: do this date lookup with the academic calendar
						Student newProvider = p.a;
						if(provider == newProvider) {
							newProvider = p.b;
						}
						
						if(newProvider.isD3()) {
							System.err.println("Other provider is also ID3: " + p);
						} else if(newProvider == Student.PLACEHOLDER) {
							System.err.println("Other provider is a placeholder, skipping ID3 "+provider.id+" as a provider on " + date);
							continue;
						} else {
							provider = newProvider;
						}
					}

					chairLabel = tuple.getRight();
				}
				
				// Select the chair
				String lookup = "";
				if(provider.practice == GroupPractice.F) {
					lookup = provider.id.substring(3);
				} else {
					lookup = provider.cluster.name().substring(1) + "_" + (provider.isUpperStudent() ? "U" : "L") + "_" + provider.pod;
				}
				ChairPosition chair = mapper.toChairPosition(lookup);
				if(floormap.get(chair) != null) { // If chair is already assigned, find another chair with whatever chairs are left
					//System.err.println("Chair " + mapper.get(chair) + " ["+lookup+"] is used more than once on " + date);
					orphans.add(p);
					continue;
				}
				
				ChairAssignment assignment = new ChairAssignment(date, p, provider);
				floormap.put(chair, assignment);
			}
			
			if(orphans.size() > 0) {
				floormap.putAll(
					ORPHAN.assignChairs(
						date, 
						orphans, 
						mapper.newPool(floormap.entrySet().stream().filter(e -> e.getValue() == null).collect(Collectors.mapping(e -> e.getKey(), Collectors.toList()))), 
						pairProcessor
					)
				);
			}
			
			return floormap;
		}
	},
	ORPHAN {
		@Override
		public Map<ChairPosition, ChairAssignment> assignChairs(DatedSession date, List<Pairing> pairs, ChairMapper mapper, Function<Pairing, Pair<Student, String>> pairProcessor) {
			
			if(mapper.mode != ChairMapper.AccessMode.ITERATIVE)
				throw new IllegalArgumentException("ChairMapper does not an iterative mode: " + mapper.mode);
			
			Map<ChairPosition, ChairAssignment> floormap = new EnumMap<ChairPosition, ChairAssignment>(ChairPosition.class);
			for(Pairing p : pairs) {

				if(mapper.hasNoChairsToMap()) {
					GroupPractice practice = p.a != Student.PLACEHOLDER ? p.a.practice : p.b.practice;
					System.err.println("Orphan for GP-" + practice + " on " + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ", " + date + " for " + p.a.id + "|" + p.b.id );
					continue;
				}

				// Set the provider and the chair label
				Student provider;
				String chairLabel;
				{
					Pair<Student, String> tuple = pairProcessor.apply(p);
					if(tuple == null) {
						continue;
					}
					provider = tuple.getLeft();
					chairLabel = tuple.getRight();
				}
				
				// Select the chair
				String lookup = "";
				if(provider.practice == GroupPractice.F) {
					lookup = provider.id.substring(3);
				} else {
					lookup = provider.cluster.name().substring(1) + "_" + (provider.isUpperStudent() ? "U" : "L") + "_" + provider.pod;
				}
				ChairPosition chair = mapper.toChairPosition(lookup);
				if(floormap.get(chair) != null) {
					throw new RuntimeException("Chair " + mapper.get(chair) + " is used more than once on " + date);
				}
				
				ChairAssignment assignment = new ChairAssignment(date, p, provider);
				floormap.put(chair, assignment);
			}
			
			return floormap;
		}
	},
	FRI_PM {
		@Override
		public Map<ChairPosition, ChairAssignment> assignChairs(DatedSession date, List<Pairing> pairs, ChairMapper mapper, Function<Pairing, Pair<Student, String>> pairProcessor) {
			
			if(date.getDayOfWeek() != DayOfWeek.FRIDAY) {
				throw new IllegalArgumentException("Date ["+date+"] is not a Friday: " + date.getDayOfWeek());
			} else if(!date.isPM()) {
				throw new IllegalArgumentException("Period is not PM: " + date.getPeriod());
			}
			
			Map<ChairPosition, ChairAssignment> floormap = new EnumMap<ChairPosition, ChairAssignment>(ChairPosition.class);
			for(Pairing p : pairs) {

				if(mapper.hasNoChairsToMap()) {
					GroupPractice practice = p.a != Student.PLACEHOLDER ? p.a.practice : p.b.practice;
					System.err.println("Orphan for GP-" + practice + " on " + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ", " + date + " for " + p.a.id + "|" + p.b.id );
					continue;
				}

				// Set the provider and the chair label
				Student provider;
				String chairLabel;
				{
					Pair<Student, String> tuple = pairProcessor.apply(p);
					if(tuple == null) {
						continue;
					}
					provider = tuple.getLeft();
					chairLabel = tuple.getRight();
				}
				
				// Select the chair
				String lookup = "";
				if(provider.practice == GroupPractice.F) {
					lookup = provider.id.substring(3);
				} else {
					lookup = provider.cluster.name().substring(1) + "_" + (provider.isUpperStudent() ? "U" : "L") + "_" + provider.pod;
				}
				ChairPosition chair = mapper.toChairPosition(lookup);
				if(floormap.get(chair) != null) {
					throw new RuntimeException("Chair " + mapper.get(chair) + " is used more than once on " + date);
				}
				
				ChairAssignment assignment = new ChairAssignment(date, p, provider);
				floormap.put(chair, assignment);
			}
			
			return floormap;
		}
	},
	PERIO {
		@Override
		public Map<ChairPosition, ChairAssignment> assignChairs(DatedSession date, List<Pairing> pairs, ChairMapper mapper, Function<Pairing, Pair<Student, String>> pairProcessor) {
			
			Map<ChairPosition, ChairAssignment> floormap = new EnumMap<ChairPosition, ChairAssignment>(ChairPosition.class);
			for(Pairing p : pairs) {

				if(p.type != PairingType.ORPHAN) {
					throw new RuntimeException("Pairing has unexpected type: " + p.type);
				}
				
				if(mapper.hasNoChairsToMap()) {
					System.err.println("Orphan for Perio on " + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ", " + date + " for " + p.getSoloStudent() );
					continue;
				}

				// Set the provider and the chair label
				Student provider;
				String chairLabel;
				{
					Pair<Student, String> tuple = pairProcessor.apply(p);
					if(tuple == null) {
						continue;
					}
					provider = tuple.getLeft();
					chairLabel = tuple.getRight();
				}
				
				// Select the chair
				String lookup = "";
				if(provider.practice != GroupPractice.F) {
					lookup = provider.cluster.name().substring(1) + "_" + (provider.isUpperStudent() ? "U" : "L") + "_" + provider.pod;
				}
				ChairPosition chair = mapper.toChairPosition(lookup);
				if(floormap.get(chair) != null) {
					throw new RuntimeException("Chair " + mapper.get(chair) + " is used more than once on " + date);
				}

				ChairAssignment assignment = new ChairAssignment(date, p, provider);
				floormap.put(chair, assignment);
			}
			
			return floormap;
		}
	};
	
	public abstract Map<ChairPosition, ChairAssignment> assignChairs(DatedSession date, List<Pairing> pairs, ChairMapper mapper, Function<Pairing, Pair<Student, String>> pairProcessor);
	
	public static class ChairMapper {
		
		private static enum AccessMode {
			RANDOM,
			ITERATIVE,
		};
		
		protected final Map<ChairPosition, String> mapper;
		protected final List<ChairPosition> listMap;
		protected final AccessMode mode;
		
		/**
		 * Builds a chair mapper object 
		 * @param mapper a map of chair position to a chair name
		 * @param pool a subset of keys from the map to focus on
		 */
		private ChairMapper(Map<ChairPosition, String> mapper, List<ChairPosition> pool, AccessMode mode) {
			this.mapper = mapper;
			this.listMap = pool;
			this.mode = mode;
		}
		
		/**
		 * Transforms lookup to a ChairPosition. If this object has an AccessMode of ITERATIVE, too many calls will
		 * result in a IndexOutOfBoundsException due to the list becoming empty
		 * @param lookup
		 * @return a ChairPosition if it exists
		 * @throws IndexOutOfBoundsException when there are no more chairs available
		 */
		public ChairPosition toChairPosition(String lookup) {
			switch(mode) {
			case RANDOM: return ChairPosition.valueOf(lookup);
			case ITERATIVE: return listMap.remove(0);
			default:
				throw new RuntimeException("Unknown mode: " + mode);
			}
		}
		
		public ChairPosition getLastChair() {
			switch(mode) {
			case RANDOM: throw new RuntimeException("Cannot get last chair with RANDOM mode");
			case ITERATIVE: return listMap.remove(listMap.size()-1);
			default:
				throw new RuntimeException("Unknown mode: " + mode);
			}
		}
		
		public String get(ChairPosition c) {
			return mapper.get(c);
		}
		
		public boolean hasNoChairsToMap() {
			return listMap.size() == 0;
		}

		public int chairsRemaining() {
			return listMap.size();
		}
		
		/**
		 * Returns a new iterative ChairMapper using the provided list
		 * @param l
		 * @return
		 */
		public ChairMapper newPool(List<ChairPosition> l) {
			return new ChairMapper(mapper, l, AccessMode.ITERATIVE);
		}

		@Deprecated
		public static ChairMapper of(Map<ChairPosition, String> mapper) {
			return new ChairMapper(mapper, new ArrayList<ChairPosition>(mapper.keySet()), AccessMode.RANDOM);
		}

		@Deprecated
		public static ChairMapper ofIterative(Map<ChairPosition, String> mapper) {
			List<ChairPosition> l = new ArrayList<ChairPosition>(mapper.keySet());
			return new ChairMapper(mapper, IntStream.range(0, l.size()).filter(n -> n % 2 == 0).mapToObj(l::get).collect(Collectors.toList()), AccessMode.ITERATIVE);
		}
		
		public static ChairMapper ofIterativeFull(Map<ChairPosition, String> mapper) {
			return new ChairMapper(mapper, new ArrayList<ChairPosition>(new TreeSet<ChairPosition>(mapper.keySet())), AccessMode.ITERATIVE);
		}
		
		@Deprecated
		public static ChairMapper ofIterativeBuchanan() {
			return new ChairMapper(buchananChairMapper, new ArrayList<ChairPosition>(buchananChairMapper.keySet()), AccessMode.ITERATIVE) {

				private Map<Integer, ChairPosition> _lookup = Map.ofEntries(
					Map.entry(1, ChairPosition._12_U_1),
					Map.entry(2, ChairPosition._12_L_1),
					Map.entry(3, ChairPosition._12_U_2),
					Map.entry(4, ChairPosition._12_L_2),
					Map.entry(5, ChairPosition._12_U_3),
					Map.entry(6, ChairPosition._12_L_3),
					Map.entry(7, ChairPosition._12_U_4),
					Map.entry(8, ChairPosition._12_L_4),
					Map.entry(9, ChairPosition._34_U_1),
					Map.entry(10, ChairPosition._34_L_1),
					Map.entry(11, ChairPosition._34_U_2),
					Map.entry(12, ChairPosition._34_L_2),
					Map.entry(13, ChairPosition._34_U_3),
					Map.entry(14, ChairPosition._34_L_3),
					Map.entry(15, ChairPosition._34_U_4),
					Map.entry(16, ChairPosition._34_L_4),
					Map.entry(17, ChairPosition._99_U_1),
					Map.entry(18, ChairPosition._99_L_1),
					Map.entry(19, ChairPosition._99_U_2),
					Map.entry(20, ChairPosition._99_L_2)
				);
				
				@Override
				public ChairPosition toChairPosition(String lookup) {
					listMap.remove(0);
					int i = Integer.parseInt(lookup);
					if(AcademicCalendar.CURRENT_YEAR == AcademicCalendar.AY2022_2023 && i == 21) {
						i = 9;
					}
					return _lookup.get(i); 
				}
			};
		}
	}
	
	public static class ChairAssignment {
		public final DatedSession session;
		public final Pairing pairing;
		private Student assigned;
		private boolean toggled = false;

		ChairAssignment(DatedSession session, Pairing pairing, Student assigned) {
			this.session = session;
			this.pairing = pairing;
			if(this.pairing.a != assigned && this.pairing.b != assigned) {
				throw new IllegalArgumentException("Student ["+assigned.id+"] is not associated with this pairing: " + pairing);
			}
			this.assigned = assigned;
		}
		
		public Student getAssigned() {
			return assigned;
		}
		
		public boolean hasToggled() {
			return toggled;
		}
		
		public void toggleAssigned() {
			
			if(toggled) {
				throw new RuntimeException("This is only to be called once per lifetime");
			}
			
			if(assigned == pairing.a) {
				assigned = pairing.b;
			} else if(assigned == pairing.b) {
				assigned = pairing.a;
			} else {
				throw new RuntimeException("Something really bad happened");
			}
			
			toggled = true;
		}
		
		@Override
		public String toString() {
			return new StringBuilder("cAssignment{")
				.append("session=").append(session).append(';')
				.append("pairing=").append(pairing.a.id).append(pairing.a == assigned ? "*" : "").append('-').append(pairing.b.id).append(pairing.b == assigned ? "*" : "")
				.append('}')
				.toString();
		}
	}
	
	@Deprecated
	public static enum ChairPosition {
		_12_U_1,
		_12_L_1,
		_12_U_2,
		_12_L_2,
		_12_U_3,
		_12_L_3,
		_12_U_4,
		_12_L_4,
		_34_U_1,
		_34_L_1,
		_34_U_2,
		_34_L_2,
		_34_U_3,
		_34_L_3,
		_34_U_4,
		_34_L_4,
		_99_U_1,
		_99_L_1,
		_99_U_2,
		_99_L_2,
		ER;
		
		public static Map<ChairPosition, ChairAssignment> getEmptyFloor() {
			Map<ChairPosition, ChairAssignment> floormap = new TreeMap<ChairPosition, ChairAssignment>();
			for(ChairPosition p : ChairPosition.values()) {
				if(!p.toString().contains("99")) {
					floormap.put(p, null);
				}
			}
			return floormap;
		}
	}

	@Deprecated
	private final static List<Pair<ChairPosition, ChairPosition>> SHARED_POSITION_PAIRING = List.of(
		Pair.of(ChairPosition._12_U_1, ChairPosition._12_L_1),
		Pair.of(ChairPosition._12_U_2, ChairPosition._12_L_2),
		Pair.of(ChairPosition._12_U_3, ChairPosition._12_L_3),
		Pair.of(ChairPosition._12_U_4, ChairPosition._12_L_4),
		Pair.of(ChairPosition._34_U_1, ChairPosition._34_L_1),
		Pair.of(ChairPosition._34_U_2, ChairPosition._34_L_2),
		Pair.of(ChairPosition._34_U_3, ChairPosition._34_L_3),
		Pair.of(ChairPosition._34_U_4, ChairPosition._34_L_4)
	); 

	@Deprecated
	public static Map<ChairPosition, String> getChairMapperForCompressedPractice(List<String> l) {
		Iterator<Pair<ChairPosition, ChairPosition>> iter = SHARED_POSITION_PAIRING.iterator();
		Map<ChairPosition, String> map = new EnumMap<ChairPosition, String>(ChairPosition.class);
		for(String s : l) {
			var p = iter.next();
			map.put(p.getLeft(), s);
			map.put(p.getRight(), s);
		}
		return map;
	}
	
	public static Map<ChairPosition, String> perioChairMapper = Map.ofEntries(
		Map.entry(ChairPosition._12_U_1, "GS9"),
		Map.entry(ChairPosition._12_L_1, "GS10"),
		Map.entry(ChairPosition._12_U_2, "GS11"),
		Map.entry(ChairPosition._12_L_2, "GS12"),
		Map.entry(ChairPosition._12_U_3, "GS16"),
		Map.entry(ChairPosition._12_L_3, "GS15"),
		Map.entry(ChairPosition._12_U_4, "GS14"),
		Map.entry(ChairPosition._12_L_4, "GS13"),
		Map.entry(ChairPosition._34_U_1, "GS17"),
		Map.entry(ChairPosition._34_L_1, "GS18"),
		Map.entry(ChairPosition._34_U_2, "GS19"),
		Map.entry(ChairPosition._34_L_2, "GS24"),
		Map.entry(ChairPosition._34_U_3, "GS23"),
		Map.entry(ChairPosition._34_L_3, "GS22")
	);
	
	@Deprecated
	public static Map<ChairPosition, String> buchananChairMapper = Map.ofEntries(
		Map.entry(ChairPosition._12_U_1, "BDI1"),
		Map.entry(ChairPosition._12_L_1, "BDI2"),
		Map.entry(ChairPosition._12_U_2, "BDI3"),
		Map.entry(ChairPosition._12_L_2, "BDI4"),
		Map.entry(ChairPosition._12_U_3, "BDI5"),
		Map.entry(ChairPosition._12_L_3, "BDI6"),
		Map.entry(ChairPosition._12_U_4, "BDI7"),
		Map.entry(ChairPosition._12_L_4, "BDI8"),
		Map.entry(ChairPosition._34_U_1, "BDI9"),
		Map.entry(ChairPosition._34_L_1, "BDI10"),
		Map.entry(ChairPosition._34_U_2, "BDI11"),
		Map.entry(ChairPosition._34_L_2, "BDI12"),
		Map.entry(ChairPosition._34_U_3, "BDI13"),
		Map.entry(ChairPosition._34_L_3, "BDI14"),
		Map.entry(ChairPosition._34_U_4, "BDI15"),
		Map.entry(ChairPosition._34_L_4, "BDI16"),
		Map.entry(ChairPosition._99_U_1, "BDI17"),
		Map.entry(ChairPosition._99_L_1, "BDI18"),
		Map.entry(ChairPosition._99_U_2, "BDI19"),
		Map.entry(ChairPosition._99_L_2, "BDI20")
	);

	public final static List<String> perioChairs = List.of(
		"D9", "D10", "D11", "D12",
		"D16", "D15", "D14", "D13",
		"D17", "D18", "D19",
		"D24", "D23", "D22"
	);

	public final static Map<GroupPractice, Map<ChairPosition, String>> friPMChairMapper = Map.ofEntries(
		Map.entry(GroupPractice.A, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B5"),
			Map.entry(ChairPosition._12_L_1, "B4"),
			Map.entry(ChairPosition._12_U_2, "B6"),
			Map.entry(ChairPosition._12_L_2, "B3"),
			Map.entry(ChairPosition._12_U_3, "B2")
		)),
		Map.entry(GroupPractice.C, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B1"),
			Map.entry(ChairPosition._12_L_1, "B8"),
			Map.entry(ChairPosition._12_U_2, "B7"),
			Map.entry(ChairPosition._12_L_2, "B9"),
			Map.entry(ChairPosition._12_U_3, "B16")
		)),
		Map.entry(GroupPractice.B, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B13"),
			Map.entry(ChairPosition._12_L_1, "B12"),
			Map.entry(ChairPosition._12_U_2, "B14"),
			Map.entry(ChairPosition._12_L_2, "B11"),
			Map.entry(ChairPosition._12_U_3, "B10")
		)),
		Map.entry(GroupPractice.F, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B21"),
			Map.entry(ChairPosition._12_L_1, "B20"),
			Map.entry(ChairPosition._12_U_2, "B22"),
			Map.entry(ChairPosition._12_L_2, "B19"),
			Map.entry(ChairPosition._12_U_3, "B23")
		)),
		Map.entry(GroupPractice.E, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B29"),
			Map.entry(ChairPosition._12_L_1, "B28"),
			Map.entry(ChairPosition._12_U_2, "B30"),
			Map.entry(ChairPosition._12_L_2, "B27"),
			Map.entry(ChairPosition._12_U_3, "B31")
		)),
		Map.entry(GroupPractice.D, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B26"),
			Map.entry(ChairPosition._12_L_1, "B32"),
			Map.entry(ChairPosition._12_U_2, "B25"),
			Map.entry(ChairPosition._12_L_2, "B24"),
			Map.entry(ChairPosition._12_U_3, "B17")
		))
	);
	
	public final static Map<GroupPractice, Map<ChairPosition, String>> friPMChairMapperSummer = Map.ofEntries(
		Map.entry(GroupPractice.D, Map.ofEntries(
			Map.entry(ChairPosition._34_U_1, "A20"),
			Map.entry(ChairPosition._34_L_1, "A17"),
			Map.entry(ChairPosition._34_U_2, "A19"),
			Map.entry(ChairPosition._34_L_2, "A18"),
			Map.entry(ChairPosition._34_U_3, "A8"),
			Map.entry(ChairPosition._34_L_3, "A5"),
			Map.entry(ChairPosition._34_U_4, "A7"),
			Map.entry(ChairPosition._34_L_4, "A6")
		)),
		Map.entry(GroupPractice.E, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "A44"),
			Map.entry(ChairPosition._12_L_1, "A41"),
			Map.entry(ChairPosition._12_U_2, "A43"),
			Map.entry(ChairPosition._12_L_2, "A42"),
			Map.entry(ChairPosition._12_U_3, "A32"),
			Map.entry(ChairPosition._12_L_3, "A29"),
			Map.entry(ChairPosition._12_U_4, "A31"),
			Map.entry(ChairPosition._12_L_4, "A30")
		)),
		Map.entry(GroupPractice.C, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "A24"),
			Map.entry(ChairPosition._12_L_1, "A13"),
			Map.entry(ChairPosition._12_U_2, "A23"),
			Map.entry(ChairPosition._12_L_2, "A14"),
			Map.entry(ChairPosition._12_U_3, "A22"),
			Map.entry(ChairPosition._12_L_3, "A15"),
			Map.entry(ChairPosition._12_U_4, "A21"),
			Map.entry(ChairPosition._12_L_4, "A16")
		)),
		Map.entry(GroupPractice.B, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "A48"),
			Map.entry(ChairPosition._12_L_1, "A37"),
			Map.entry(ChairPosition._12_U_2, "A47"),
			Map.entry(ChairPosition._12_L_2, "A38"),
			Map.entry(ChairPosition._12_U_3, "A46"),
			Map.entry(ChairPosition._12_L_3, "A39"),
			Map.entry(ChairPosition._12_U_4, "A45"),
			Map.entry(ChairPosition._12_L_4, "A40")
		)),
		Map.entry(GroupPractice.A, Map.ofEntries(
			Map.entry(ChairPosition._34_U_1, "A12"),
			Map.entry(ChairPosition._34_L_1, "A1"),
			Map.entry(ChairPosition._34_U_2, "A11"),
			Map.entry(ChairPosition._34_L_2, "A2"),
			Map.entry(ChairPosition._34_U_3, "A10"),
			Map.entry(ChairPosition._34_L_3, "A3"),
			Map.entry(ChairPosition._34_U_4, "A9"),
			Map.entry(ChairPosition._34_L_4, "A4")
		)),
		Map.entry(GroupPractice.F, Map.ofEntries(
			Map.entry(ChairPosition._34_U_1, "A36"),
			Map.entry(ChairPosition._34_L_1, "A25"),
			Map.entry(ChairPosition._34_U_2, "A35"),
			Map.entry(ChairPosition._34_L_2, "A26"),
			Map.entry(ChairPosition._34_U_3, "A34"),
			Map.entry(ChairPosition._34_L_3, "A27"),
			Map.entry(ChairPosition._34_U_4, "A33"),
			Map.entry(ChairPosition._34_L_4, "A28")				
		))
	);
	

	@Deprecated
	public final static Map<GroupPractice, List<String>> friSummerChairMapper = Map.ofEntries(
		Map.entry(GroupPractice.A, List.of(
			"A20", "A19", 
			"A17", "A18",
			"A8", "A7", 
			"A5", "A6")
		),
		Map.entry(GroupPractice.B, List.of(
			"A48", "A47", "A46" ,"A45",
			"A37", "A38", "A39", "A40")
		),
		Map.entry(GroupPractice.D, List.of(
			"A36", "A35", "A34" ,"A33",
			"A25", "A26", "A27", "A28")
		),
		Map.entry(GroupPractice.C, List.of(
			"A24", "A23", "A22" ,"A21",
			"A13", "A14", "A15", "A16")
		),
		Map.entry(GroupPractice.E, List.of(
			"A44", "A43", 
			"A41", "A42",
			"A32", "A31", 
			"A29", "A30")
		),
		Map.entry(GroupPractice.F, List.of(
			"A12", "A11", "A10" ,"A9",
			"A1", "A2", "A3", "A4")
		)
	);
	
	@Deprecated
	public final static List<List<String>> friChairOptionsBreak = List.of(
		List.of("B29", "B30", "B31", 
				"B28", "B27", "B26"),
		List.of("B21", "B22", "B23",
				"B20", "B19", "B18"),
		List.of("B13", "B14", "B15",
				"B12", "B11", "B10"),
		List.of("B5", "B6", "B7",
				"B4", "B3", "B2"),
		List.of("B32", "B25", "B24",
				"B17", "B16", "B9")
	);

	@Deprecated
	public final static List<List<String>> friChairOptions = List.of(
		List.of("B29", "B30", "B31", "B32", 
				"B28", "B27", "B26", "B25"),
		List.of("B21", "B22", "B23", "B24",
				"B20", "B19", "B18", "B17"),
		List.of("B13", "B14", "B15", "B16",
				"B12", "B11", "B10", "B9"),
		List.of("B5", "B6", "B7", "B8",
				"B4", "B3", "B2", "B1")
	);

	// TODO: convert this to use org.soreni.xo.objects.ChairReservation.Chair
	public static Map<GroupPractice, Map<ChairPosition, String>> chairMapper = Map.ofEntries(
		Map.entry(GroupPractice.E, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B29"),
			Map.entry(ChairPosition._12_L_1, "B28"),
			Map.entry(ChairPosition._12_U_2, "B30"),
			Map.entry(ChairPosition._12_L_2, "B27"),
			Map.entry(ChairPosition._12_U_3, "B31"),
			Map.entry(ChairPosition._12_L_3, "B26"),
			Map.entry(ChairPosition._12_U_4, "B32"),
			Map.entry(ChairPosition._12_L_4, "B25"),
			Map.entry(ChairPosition._34_U_1, "B21"),
			Map.entry(ChairPosition._34_L_1, "B20"),
			Map.entry(ChairPosition._34_U_2, "B22"),
			Map.entry(ChairPosition._34_L_2, "B19"),
			Map.entry(ChairPosition._99_L_1, "B2")
		)),
		Map.entry(GroupPractice.D, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B12"),
			Map.entry(ChairPosition._12_L_1, "B13"),
			Map.entry(ChairPosition._12_U_2, "B11"),
			Map.entry(ChairPosition._12_L_2, "B14"),
			Map.entry(ChairPosition._12_U_3, "B10"),
			Map.entry(ChairPosition._12_L_3, "B15"),
			Map.entry(ChairPosition._12_U_4, "B9"),
			Map.entry(ChairPosition._12_L_4, "B16"),
			Map.entry(ChairPosition._34_U_3, "B17"),
			Map.entry(ChairPosition._34_L_3, "B24"),
			Map.entry(ChairPosition._34_U_4, "B18"),
			Map.entry(ChairPosition._34_L_4, "B23"),
//			Map.entry(ChairPosition._34_U_1, "B5"),
//			Map.entry(ChairPosition._34_L_1, "B4"),
//			Map.entry(ChairPosition._34_U_2, "B6"),
//			Map.entry(ChairPosition._34_L_2, "B3"),
//			Map.entry(ChairPosition._34_U_3, "B7"),
//			Map.entry(ChairPosition._34_L_3, "B2"),
//			Map.entry(ChairPosition._34_U_4, "B8"),
//			Map.entry(ChairPosition._34_L_4, "B1")
			Map.entry(ChairPosition._99_L_1, "B7")
		)),
		Map.entry(GroupPractice.C, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "A24"),
			Map.entry(ChairPosition._12_L_1, "A13"),
			Map.entry(ChairPosition._12_U_2, "A23"),
			Map.entry(ChairPosition._12_L_2, "A14"),
			Map.entry(ChairPosition._12_U_3, "A22"),
			Map.entry(ChairPosition._12_L_3, "A15"),
			Map.entry(ChairPosition._12_U_4, "A21"),
			Map.entry(ChairPosition._12_L_4, "A16"),
			Map.entry(ChairPosition._34_U_1, "A20"),
			Map.entry(ChairPosition._34_L_1, "A17"),
			Map.entry(ChairPosition._34_U_2, "A19"),
			Map.entry(ChairPosition._34_L_2, "A18"),
			Map.entry(ChairPosition._99_L_1, "B3")
		)),
		Map.entry(GroupPractice.B, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "A48"),
			Map.entry(ChairPosition._12_L_1, "A37"),
			Map.entry(ChairPosition._12_U_2, "A47"),
			Map.entry(ChairPosition._12_L_2, "A38"),
			Map.entry(ChairPosition._12_U_3, "A46"),
			Map.entry(ChairPosition._12_L_3, "A39"),
			Map.entry(ChairPosition._12_U_4, "A45"),
			Map.entry(ChairPosition._12_L_4, "A40"),
			Map.entry(ChairPosition._34_U_1, "A44"),
			Map.entry(ChairPosition._34_L_1, "A41"),
			Map.entry(ChairPosition._34_U_2, "A43"),
			Map.entry(ChairPosition._34_L_2, "A42"),
			Map.entry(ChairPosition._99_L_1, "B5")
		)),
		Map.entry(GroupPractice.A, Map.ofEntries(
			Map.entry(ChairPosition._12_U_3, "A12"),
			Map.entry(ChairPosition._12_L_3, "A1"),
			Map.entry(ChairPosition._12_U_4, "A11"),
			Map.entry(ChairPosition._12_L_4, "A2"),
			Map.entry(ChairPosition._34_U_1, "A10"),
			Map.entry(ChairPosition._34_L_1, "A3"),
			Map.entry(ChairPosition._34_U_2, "A9"),
			Map.entry(ChairPosition._34_L_2, "A4"),
			Map.entry(ChairPosition._34_U_3, "A8"),
			Map.entry(ChairPosition._34_L_3, "A5"),
			Map.entry(ChairPosition._34_U_4, "A7"),
			Map.entry(ChairPosition._34_L_4, "A6"),
			Map.entry(ChairPosition._99_L_1, "B4")
		)),
		Map.entry(GroupPractice.F, Map.ofEntries(
			Map.entry(ChairPosition._12_U_3, "A36"),
			Map.entry(ChairPosition._12_L_3, "A25"),
			Map.entry(ChairPosition._12_U_4, "A35"),
			Map.entry(ChairPosition._12_L_4, "A26"),
			Map.entry(ChairPosition._34_U_1, "A34"),
			Map.entry(ChairPosition._34_L_1, "A27"),
			Map.entry(ChairPosition._34_U_2, "A33"),
			Map.entry(ChairPosition._34_L_2, "A28"),
			Map.entry(ChairPosition._34_U_3, "A32"),
			Map.entry(ChairPosition._34_L_3, "A29"),
			Map.entry(ChairPosition._34_U_4, "A31"),
			Map.entry(ChairPosition._34_L_4, "A30"),				
			Map.entry(ChairPosition._99_L_1, "B6")
		))
	);
	
	@Deprecated
	public static Map<GroupPractice, Map<ChairPosition, String>> chairMapperAY2223 = Map.ofEntries(
		Map.entry(GroupPractice.E, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B29"),
			Map.entry(ChairPosition._12_L_1, "B28"),
			Map.entry(ChairPosition._12_U_2, "B30"),
			Map.entry(ChairPosition._12_L_2, "B27"),
			Map.entry(ChairPosition._12_U_3, "B31"),
			Map.entry(ChairPosition._12_L_3, "B26"),
			Map.entry(ChairPosition._12_U_4, "B32"),
			Map.entry(ChairPosition._12_L_4, "B25"),
			Map.entry(ChairPosition._34_U_1, "B21"),
			Map.entry(ChairPosition._34_L_1, "B20"),
			Map.entry(ChairPosition._34_U_2, "B22"),
			Map.entry(ChairPosition._34_L_2, "B19"),
			Map.entry(ChairPosition._34_U_3, "B23"),
			Map.entry(ChairPosition._34_L_3, "B18"),
			Map.entry(ChairPosition._34_U_4, "B24"),
			Map.entry(ChairPosition._34_L_4, "B17")
		)),
		Map.entry(GroupPractice.D, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "B13"),
			Map.entry(ChairPosition._12_L_1, "B12"),
			Map.entry(ChairPosition._12_U_2, "B14"),
			Map.entry(ChairPosition._12_L_2, "B11"),
			Map.entry(ChairPosition._12_U_3, "B15"),
			Map.entry(ChairPosition._12_L_3, "B10"),
			Map.entry(ChairPosition._12_U_4, "B16"),
			Map.entry(ChairPosition._12_L_4, "B9"),
			Map.entry(ChairPosition._34_U_1, "B5"),
			Map.entry(ChairPosition._34_L_1, "B4"),
			Map.entry(ChairPosition._34_U_2, "B6"),
			Map.entry(ChairPosition._34_L_2, "B3"),
			Map.entry(ChairPosition._34_U_3, "B7"),
			Map.entry(ChairPosition._34_L_3, "B2"),
			Map.entry(ChairPosition._34_U_4, "B8"),
			Map.entry(ChairPosition._34_L_4, "B1")
		)),
		Map.entry(GroupPractice.C, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "A24"),
			Map.entry(ChairPosition._12_L_1, "A13"),
			Map.entry(ChairPosition._12_U_2, "A23"),
			Map.entry(ChairPosition._12_L_2, "A14"),
			Map.entry(ChairPosition._12_U_3, "A22"),
			Map.entry(ChairPosition._12_L_3, "A15"),
			Map.entry(ChairPosition._12_U_4, "A21"),
			Map.entry(ChairPosition._12_L_4, "A16"),
			Map.entry(ChairPosition._34_U_1, "A12"),
			Map.entry(ChairPosition._34_L_1, "A1"),
			Map.entry(ChairPosition._34_U_2, "A11"),
			Map.entry(ChairPosition._34_L_2, "A2"),
			Map.entry(ChairPosition._34_U_3, "A10"),
			Map.entry(ChairPosition._34_L_3, "A3"),
			Map.entry(ChairPosition._34_U_4, "A9"),
			Map.entry(ChairPosition._34_L_4, "A4")
		)),
		Map.entry(GroupPractice.B, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "A48"),
			Map.entry(ChairPosition._12_L_1, "A37"),
			Map.entry(ChairPosition._12_U_2, "A47"),
			Map.entry(ChairPosition._12_L_2, "A38"),
			Map.entry(ChairPosition._12_U_3, "A46"),
			Map.entry(ChairPosition._12_L_3, "A39"),
			Map.entry(ChairPosition._12_U_4, "A45"),
			Map.entry(ChairPosition._12_L_4, "A40"),
			Map.entry(ChairPosition._34_U_1, "A36"),
			Map.entry(ChairPosition._34_L_1, "A25"),
			Map.entry(ChairPosition._34_U_2, "A35"),
			Map.entry(ChairPosition._34_L_2, "A26"),
			Map.entry(ChairPosition._34_U_3, "A34"),
			Map.entry(ChairPosition._34_L_3, "A27"),
			Map.entry(ChairPosition._34_U_4, "A33"),
			Map.entry(ChairPosition._34_L_4, "A28")
		)),
		Map.entry(GroupPractice.A, Map.ofEntries(
			Map.entry(ChairPosition._12_U_1, "A44"),
			Map.entry(ChairPosition._12_L_1, "A41"),
			Map.entry(ChairPosition._12_U_2, "A43"),
			Map.entry(ChairPosition._12_L_2, "A42"),
			Map.entry(ChairPosition._12_U_3, "A32"),
			Map.entry(ChairPosition._12_L_3, "A29"),
			Map.entry(ChairPosition._12_U_4, "A31"),
			Map.entry(ChairPosition._12_L_4, "A30"),
			Map.entry(ChairPosition._34_U_1, "A20"),
			Map.entry(ChairPosition._34_L_1, "A17"),
			Map.entry(ChairPosition._34_U_2, "A19"),
			Map.entry(ChairPosition._34_L_2, "A18"),
			Map.entry(ChairPosition._34_U_3, "A8"),
			Map.entry(ChairPosition._34_L_3, "A5"),
			Map.entry(ChairPosition._34_U_4, "A7"),
			Map.entry(ChairPosition._34_L_4, "A6")
		))
	);

	@Deprecated
	public static Student selectProvider(Pairing pair, Period period, boolean upperPriority) {
		return selectProvider(pair, period, upperPriority ? UpperLower.UPPER : UpperLower.LOWER);
	}
	
	public static Student selectProvider(Pairing pair, Period period, UpperLower priority) {

		if(priority == UpperLower.NEITHER || priority == UpperLower.UNKNOWN) {
			throw new IllegalArgumentException("Passed priority needs to be UPPER or LOWER");
		}

		Student s = pair.a;
		if(s == Student.PLACEHOLDER) {
			s = pair.b;
		}
		switch(pair.type) {
		case PRIMARY:
		case SECOND_42:
		case SECOND_32:
			if(priority == UpperLower.UPPER) {
				return period.isAM() ? pair.b : pair.a;
			} else {
				return period.isAM() ? pair.a : pair.b;
			}
		case SECONDARY_3:
		case SECONDARY_4:
			if(priority == UpperLower.UPPER) {
				return period.isAM() ? pair.a : pair.b;
			} else {
				return period.isAM() ? pair.b : pair.a;
			}
		case SEPARATED:
			return s;
		case ORPHAN:
			if(s.isUpperStudent() && priority == UpperLower.UPPER) {
				return period.isAM() ? s : Student.PLACEHOLDER;
			} else if(!s.isUpperStudent() && !(priority == UpperLower.UPPER)) {
				return period.isAM() ? s : Student.PLACEHOLDER;
			} else {
				return period.isAM() ? Student.PLACEHOLDER : s;
			}
		default:
			throw new RuntimeException("Unknown exception");
		}
	}
	
	public static final List<PairingType> PAIRINGTYPES_OF_INTEREST = List.of(
		PairingType.PRIMARY,
		PairingType.SECONDARY_4,
		PairingType.SECONDARY_3,
		PairingType.SEPARATED,
		PairingType.ORPHAN,
		PairingType.SECOND_42,
		PairingType.SECOND_32
	);
	
	// TODO: make this calculable based on academic calendar
	public static final BiPredicate<DatedSession, Student> getERMoratorium(XOGridReader reader) {
		AcademicCalendar calendar = reader.getAcademicCalendar();
		switch(calendar) {
		case AY2022_2023:
			return (session, student) -> student.isD3() && SODDateUtils.dateIsOnOrBefore(session.date, LocalDate.of(calendar.startYear, Month.SEPTEMBER, 16)) && !(reader.isD4LectureDate(session.date) && session.isAM());
		case AY2023_2024:
			return (session, student) -> {
				if(student.isD3() && SODDateUtils.dateIsOnOrBefore(session.date, LocalDate.of(calendar.startYear, Month.SEPTEMBER, 19)) && !(reader.isD4LectureDate(session.date) && session.isAM())) {
					return true;
				} else if(student.isID3()) {
					if(SODDateUtils.dateIsOnOrBefore(session.date, LocalDate.of(calendar.endYear, Month.JANUARY, 1))) {
						return true;
					} else if(SODDateUtils.dateIsOnOrBefore(session.date, LocalDate.of(calendar.endYear, Month.APRIL, 8)) && !(reader.isD4LectureDate(session.date) && session.isAM())) { 
						return true;
					} else {
						return false;
					}
				} else {
					return false;
				}
			};
		default:
			return (s1, s2) -> false;
		}
	}
	
	// TODO: make this calculable based on academic calendar
	public static final BiPredicate<DatedSession, Student> getNPEMoratorium(AcademicCalendar calendar) {
		
		switch(calendar) {
		case AY2022_2023:
			return (session, student) -> student.isD4() && SODDateUtils.dateIsOnOrAfter(session.date, LocalDate.of(calendar.endYear, Month.MAY, 1)) && !(session.date.getDayOfWeek() == DayOfWeek.FRIDAY && session.isPM());
		case AY2023_2024:
			// TODO: what about Fri PM NPE -- D2s cover?
			return (session, student) -> student.isFourthYear() && SODDateUtils.dateIsOnOrAfter(session.date, LocalDate.of(calendar.endYear, Month.APRIL, 8)) && !(session.date.getDayOfWeek() == DayOfWeek.FRIDAY && session.isPM());
		default:
			return (s1, s2) -> false;
		}
	}
	
	public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
	
	public static List<String> buildChairAssignmentAM(LocalDate date, String sessionName, String chairName, Student s, String chairNote) {
		return buildChairAssignment(date, "AM", chairName, s, chairNote, "8:30", "11:30");
	}
	
	public static List<String> buildChairAssignmentPM1(LocalDate date, String sessionName, String chairName, Student s, String chairNote) {
		return buildChairAssignment(date, "PM1", chairName, s, chairNote, "13:00", "15:00");
	}

	public static List<String> buildChairAssignmentPM2(LocalDate date, String sessionName, String chairName, Student s, String chairNote) {
		return buildChairAssignment(date, "PM2", chairName, s, chairNote, "15:00", "17:00");
	}

	public static List<String> buildChairAssignment(LocalDate date, String sessionName, String chairName, Student s, String chairNote, String startTime, String endTime) {
		try {
			return List.of(
				DATE_FORMAT.format(date),
				sessionName,
				chairName,
				s.id,
				chairNote, // Chair Notes
				/*"",
				"",
				"",*/
				startTime,
				endTime
			);
		} catch (NullPointerException ex) {
			throw ex;
		}
	}
	
	public static class WeekSchedule {
		ChairAssignment monAM = null;
		ChairAssignment monPM = null;
		ChairAssignment tueAM = null;
		ChairAssignment tuePM = null;
		ChairAssignment wedAM = null;
		ChairAssignment wedPM = null;
		ChairAssignment thuAM = null;
		ChairAssignment thuPM = null;
		ChairAssignment friAM = null;
		ChairAssignment friPM = null;
		
		static WeekSchedule of(List<ChairAssignment> l) {
			
			WeekSchedule s = new WeekSchedule();
			for(ChairAssignment c : l) {
				switch(c.session.getDayOfWeek()) {
				case MONDAY:
					if(c.session.isAM()) {
						s.monAM = c;
					} else {
						s.monPM = c;
					}
					break;
				case TUESDAY:
					if(c.session.isAM()) {
						s.tueAM = c;
					} else {
						s.tuePM = c;
					}
					break;
				case WEDNESDAY:
					if(c.session.isAM()) {
						s.wedAM = c;
					} else {
						s.wedPM = c;
					}
					break;
				case THURSDAY:
					if(c.session.isAM()) {
						s.thuAM = c;
					} else {
						s.thuPM = c;
					}
					break;
				case FRIDAY:
					if(c.session.isAM()) {
						s.friAM = c;
					} else {
						s.friPM = c;
					}
					break;
				default:
					throw new RuntimeException("Impossible day of week: " + c.session.date);
				}
			}
			return s;
		}
	}

}
