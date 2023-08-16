package ucsf.sod.xo.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import ucsf.sod.objects.DatedSession;
import ucsf.sod.util.SODDateUtils;
import ucsf.sod.xo.ChairScheduler;
import ucsf.sod.xo.XOGridUtils;
import ucsf.sod.xo.objects.GroupPractice;
import ucsf.sod.xo.objects.Student;
import ucsf.sod.xo.objects.Student.UpperLower;

public class ERNPEScheduler implements Scheduler<DatedSession, Pairing, Student> {
	
	public static final String ER_LABEL = "ER";
	public static final String NPE_LABEL = "NPE";
	
	private static enum PairingSelectionMode {
		DEFAULT(p -> p.type == PairingType.SEPARATED || p.type == PairingType.ORPHAN),
		SECONDARY(p -> p.type != PairingType.SECONDARY_3 && p.type != PairingType.SECONDARY_4 && p.type != PairingType.PAIRED),
		PRIMARY(p -> p.type != PairingType.PRIMARY && p.type != PairingType.SECOND_42 && p.type != PairingType.SECOND_32),
		DESPERATION(p -> false);
		
		final Predicate<Pairing> filter;
		private PairingSelectionMode(Predicate<Pairing> exclusionFilter) {
			this.filter = exclusionFilter;
		}
	}

	private static final Function<PairingSelectionMode, PairingSelectionMode> ER_MODE_TOGGLE = m -> {
		if(m == PairingSelectionMode.DEFAULT) {
			return PairingSelectionMode.SECONDARY;
		} else if(m == PairingSelectionMode.SECONDARY) {
			return PairingSelectionMode.PRIMARY;
		} else {
			return null;
		}
	};

	private static final Function<PairingSelectionMode, PairingSelectionMode> NPE_MODE_TOGGLE = m -> {
		if(m == PairingSelectionMode.PRIMARY) {
			return PairingSelectionMode.SECONDARY;
		} else if(m == PairingSelectionMode.SECONDARY) {
			return PairingSelectionMode.DEFAULT;
		} else {
			return null;
		}
	};

	private static final int DEFAULT_FULL_RETRIES = 1;
	
	private final Map<DatedSession, Collection<Pairing>> pool;
	private final GroupPractice practice;
	private Map<DatedSession, Map<Student, Pairing>> cacheOfStudentsToPairing = new TreeMap<DatedSession, Map<Student, Pairing>>();
	private final String label;
	private PairingSelectionMode defaultMode;
	private PairingSelectionMode mode;
	private final Function<PairingSelectionMode, PairingSelectionMode> modeToggle;
	private final BiPredicate<DatedSession, Student> moratoriumFilter;
	private final Predicate<Student> includedStudents;
	private int retryAttempts = DEFAULT_FULL_RETRIES;

	private final Function<DatedSession, UpperLower> getPriority;
	
	private final Map<Student, Integer> remaining = new TreeMap<Student, Integer>();
	
	@Override
	public Map<Student, Integer> getRemainingMap() {
		return remaining;
	}

	private ERNPEScheduler(GroupPractice practice, Map<DatedSession, Collection<Pairing>> space, String label, PairingSelectionMode startingMode, Function<PairingSelectionMode, PairingSelectionMode> toggle, BiPredicate<DatedSession, Student> moratoriumFilter, Predicate<Student> excludedStudents, Function<DatedSession, UpperLower> getPriority) {
		this.practice = practice;
		this.pool = space;
		this.label = label;
		this.mode = defaultMode = startingMode;
		this.modeToggle = toggle;
		this.moratoriumFilter = moratoriumFilter;
		this.includedStudents = excludedStudents.negate();
		this.getPriority = getPriority;
	}

	private List<DatedSession> unassignedKeys = null;
	
	@Override
	public List<DatedSession> getUnassignedKeys() {
		return unassignedKeys == null ? new ArrayList<DatedSession>(pool.keySet()) : unassignedKeys;
	}

	@Override
	public void setUnassignedKeys(List<DatedSession> l) {
		unassignedKeys = l;
	}
	
	@Override
	public List<DatedSession> getCandidateKeys() {
		List<DatedSession> session = new ArrayList<DatedSession>(pool.keySet());
		XOGridUtils.shuffle(session);
		return session;
	}
	
	@Override
	public List<Student> getCandidateValues() {
		return practice
			.getStudentsStream(s -> s.isD3() || s.isD4() || s.isID4() || s.isID3())
			.filter(includedStudents)
//			.map(s -> Pair.of(s, usedDates.getOrDefault(s, Set.of()).size()))
//			.sorted((p1, p2) -> Integer.compare(p1.getRight(), p2.getRight()))
//			.collect(Collectors.groupingBy(
//				p -> p.getRight(),
//				Collectors.collectingAndThen(
//					Collectors.toList(), 
//					l -> l.stream().map(Pair::getLeft).collect(Collectors.toList())
//			)));
//		return null;
			.collect(Collectors.collectingAndThen(
				Collectors.toList(), 
				l -> { XOGridUtils.shuffle(l); return l; }
			));
	}

	@Override
	public Map<DatedSession, Collection<Pairing>> getSpace() {
		return pool;
	}
	
	/**
	 * Gets the mapping of students to the pairing to allow us to annotate the pairing
	 * Filters out any pairings that are labeled
	 * @param session the session by which to get the students-pairing matching
	 * @return a map of students to the pairing that they are associated with
	 */
	private Map<Student, Pairing> getCache(DatedSession session) {
		Map<Student, Pairing> m = cacheOfStudentsToPairing.get(session);
		
		if(m == null) {
			cacheOfStudentsToPairing.put(
				session, 
				m = getSpace().get(session).stream()
					.flatMap(p -> List.of(Pair.of(p.a, p), Pair.of(p.b, p)).stream())
					.filter(p -> p.getLeft() != Student.PLACEHOLDER && p.getRight().label == null)
					.collect(Collectors.toMap(Pair::getLeft, Pair::getRight))
			);
		}
		
		return m;
	}
	
	private Student forceCandidate(DatedSession candidateDate, List<Pairing> l) {
		
		if(l.size() == 0) {
			return Student.PLACEHOLDER;
		}

		XOGridUtils.shuffle(l);
		for(Pairing p : l) {
			
			boolean a = moratoriumFilter.test(candidateDate, p.a);
			boolean b = moratoriumFilter.test(candidateDate, p.b);

			Student candidate = Student.PLACEHOLDER;
			if(a) {
				if(!b) {
					candidate = p.b;
				} else {
					throw new RuntimeException("Both are moratorium: " + p + " on " + candidateDate);						
				}
			} else if(b) {
				candidate = p.a;
			} else {
				candidate = ChairScheduler.selectProvider(p, candidateDate.getPeriod(), getPriority.apply(candidateDate));
				//System.err.println("Forcing when both are not on moratorium: " + p + " on " + candidateDate);
			}

			// If we found a candidate and they are not doing the assignment this week, then force assignment
			if(candidate != Student.PLACEHOLDER && usedDates.getOrDefault(candidate, Set.of()).contains(SODDateUtils.floorToLastMonday(candidateDate.date))) {
				System.err.println("Forcing " + candidate + " on " + candidateDate + " with pairing type " + p.type);
				return candidate;
			}
		}
		
		return Student.PLACEHOLDER;
	}
	
	private boolean helper(Student s, Map<Student, Pairing> m, DatedSession candidateDate) {
		
		System.out.println("Testing " + s.id);
		
		if(!m.containsKey(s)) {
			System.out.println(true);
			return true;
		}

		System.out.println("Testing " + m.get(s) + " for " + mode);
		if(mode.filter.test(m.get(s))) {
			System.out.println("\t"+true);
			return true;
		}
		
		if(moratoriumFilter.test(candidateDate, s)) {
			System.out.println("\t\t"+true);
			return true;
		}
		
		if(mode != PairingSelectionMode.DESPERATION && usedDates.getOrDefault(s, Set.of()).contains(SODDateUtils.floorToLastMonday(candidateDate.date))) {
			System.out.println("\t\t\t"+true);
			return true;
		}
		
		System.out.println("Passed");
		return false;
	}
	
	@Override
	/**
	 * Keep only candidates that are a part of pairings that are unlabeled or the pairing meets the criteria of being selected
	 * Mutates the given candidate list
	 */
	public List<Student> filterCandidates(DatedSession candidateDate, List<Student> candidates, Collection<Pairing> options) {
		Map<Student, Pairing> m = getCache(candidateDate);

//		System.out.println("Students: " + XOGridUtils.wrapStudentSet(new TreeSet<Student>(m.keySet())));
//		System.out.println("Candidates: " + XOGridUtils.wrapStudentSet(new TreeSet<Student>(candidates)));
		
		candidates.removeIf(
			s -> {

//				return helper(s, m, candidateDate);
				
				return !m.containsKey(s) || 
				mode.filter.test(m.get(s)) ||
				moratoriumFilter.test(candidateDate, s) ||
				(mode != PairingSelectionMode.DESPERATION && usedDates.getOrDefault(s, Set.of()).contains(SODDateUtils.floorToLastMonday(candidateDate.date)));
			}
		);

		if(candidates.size() == 0) {
//			System.out.println("No candidates for " + candidateDate);
			return candidates;
		} else {
//			System.out.println("Something left");
		}
		
		Map<Integer, List<Student>> _m = candidates.stream().collect(
			Collectors.groupingBy(
				s -> usedDates.getOrDefault(s, Set.of()).size(),
				TreeMap::new,
				Collectors.toList()
		));
		candidates = _m.entrySet().stream().min(Comparator.comparing(Map.Entry::getKey)).get().getValue();
		XOGridUtils.shuffle(candidates);
		return candidates;
	}
	
	/**
	 * Keep only candidates that are a part of pairings that are unlabeled or the pairing meets the criteria of being selected
	 * Mutates the given candidate list
	 */
	public List<Student> filterCandidates_Test(DatedSession candidateDate, List<Student> candidates, Collection<Pairing> options) {
		Map<Student, Pairing> m = getCache(candidateDate);
		UpperLower priority = getPriority.apply(candidateDate);

		if(mode == PairingSelectionMode.DESPERATION) {
			
			candidates.removeIf(s -> !m.containsKey(s));
			if(candidates.size() == 0) {
				
				List<Pairing> l = options.stream().filter(p -> p.type == PairingType.PRIMARY).collect(Collectors.toList());
				if(l.size() == 0 && candidateDate.getDayOfWeek() == DayOfWeek.FRIDAY) {
					if(candidateDate.isAM()) {
						l = options.stream().filter(p -> p.type == PairingType.SECONDARY_3).collect(Collectors.toList());
					} else if(candidateDate.isPM()) {
						l = options.stream().filter(p -> p.type == PairingType.SECONDARY_4).collect(Collectors.toList());						
					}
				} else if(l.size() == 0) {
					for(Pairing p : options) {
						System.out.println(p);
					}

	 				System.out.println("\n\n\n");
				}
				
				Student candidate = forceCandidate(candidateDate, l);
				if(candidate != Student.PLACEHOLDER) {
					candidates.add(candidate);				
				}
				
				return candidates;
			}
			
			candidates.removeIf(s -> moratoriumFilter.test(candidateDate, s));
			if(candidates.size() == 0) {
				List<Pairing> l = options.stream().filter(p -> p.type == PairingType.PRIMARY).collect(Collectors.toList());
				if(l.size() == 0 && candidateDate.getDayOfWeek() == DayOfWeek.FRIDAY) {
					if(candidateDate.isAM()) {
						l = options.stream().filter(p -> p.type == PairingType.SECONDARY_3).collect(Collectors.toList());
					} else if(candidateDate.isPM()) {
						l = options.stream().filter(p -> p.type == PairingType.SECONDARY_4).collect(Collectors.toList());						
					}
				}
				
				Student candidate = forceCandidate(candidateDate, l);
				if(candidate != Student.PLACEHOLDER) {
					candidates.add(candidate);
				}
			}
			/*
			for(Pairing p : options) {
				System.out.println(p);
			}
			
			System.out.println("\n\n\nContains Key");
					
			for(Student s : candidates) {
				System.out.println("\t" + s.id + "\t" + m.containsKey(s));
			}
			
			candidates.removeIf(s -> !m.containsKey(s));
			
			for(Student s : candidates) {
				System.out.println(s);
			}
			
			System.out.println("\n\n\nMode Filter");

			candidates.removeIf(s -> mode.filter.test(m.get(s)));

			for(Student s : candidates) {
				System.out.println(s);
			}

			System.out.println("\n\n\nMoratorium Filter");

			candidates.removeIf(s -> moratoriumFilter.test(candidateDate, s));
			
			for(Student s : candidates) {
				System.out.println(s);
			}

			System.out.println("\n\n\n");

			if(candidates.size() == 0) {
				
			}
			*/
			
		} else {
			candidates.removeIf(
				s -> !m.containsKey(s) || 
				mode.filter.test(m.get(s)) ||
				s != ChairScheduler.selectProvider(m.get(s), candidateDate.getPeriod(), priority) ||
				moratoriumFilter.test(candidateDate, s) ||
				(mode != PairingSelectionMode.DESPERATION && usedDates.getOrDefault(s, Set.of()).contains(SODDateUtils.floorToLastMonday(candidateDate.date)))
			);
		}
		return candidates;
	}
	
	@Override
	public Halt handleNoResults(List<DatedSession> unassignedKeys) {
		PairingSelectionMode last = mode;
		System.out.print("Switching mode from " + mode);
		mode = modeToggle.apply(mode);
		System.out.println(" to " + mode);
		if(mode == null) {
			if(retryAttempts-- <= 0) {
				if(last == PairingSelectionMode.DESPERATION) {
					System.err.println("Desperation mode exhausted");

//					usedDates.forEach((s, l) -> {
//						System.err.println(s + "\t" + l.size());
//					});

					reportUnassignedKeys(unassignedKeys);
					return Halt.HALT;
				} else {
					System.err.println("Retry attempted, entering desperation mode");
					
//					usedDates.forEach((s, l) -> {
//						System.err.println(s + "\t" + l.size());
//					});
					
					mode = PairingSelectionMode.DESPERATION;
					return Halt.RESET;
				}
			} else {
				mode = defaultMode;				
				return Halt.RESET;
			}
		} else {
			return Halt.CONTINUE;
		}
	}
	
	@Override
	public void handlePartialResults(Map<DatedSession, Student> results, List<Student> unassignedValues, List<DatedSession> unassignedKeys) {
		Scheduler.super.handlePartialResults(results, unassignedValues, unassignedKeys);
//		System.err.println("Partial remaining:");
//		unassignedValues.forEach(s -> System.err.println("\t" + s.id));

		/*
		mode = modeToggle.apply(mode);
		if(mode == null) {
			System.out.flush();
			System.err.flush();
			throw new RuntimeException("Partial results remain with highest level");
		}
		*/
		if(mode == PairingSelectionMode.DESPERATION) {
			System.err.println(retryAttempts);
		}
	}

	@Override
	public void handleFullResults(Map<DatedSession, Student> results, List<DatedSession> unassignedKeys) {
		Scheduler.super.handleFullResults(results, unassignedKeys);		
		mode = defaultMode;
		if(mode == PairingSelectionMode.DESPERATION) {
			System.err.println("Retry attemps remaining: " + retryAttempts);
		} else {
			System.err.println("Current mode: " + mode);
		}
		if(retryAttempts == 0) {
			System.err.println("Resetting retry attempts");
		}
		retryAttempts = DEFAULT_FULL_RETRIES;
	}

	private Map<Student, Set<LocalDate>> usedDates = new TreeMap<Student, Set<LocalDate>>();

	@Override
	public void insert(Map<DatedSession, Student> m, DatedSession candidateKey, Student candidate) {
		Scheduler.super.insert(m, candidateKey, candidate);

		Set<LocalDate> dates = usedDates.get(candidate);
		if(dates == null) {
			usedDates.put(candidate, dates = new TreeSet<LocalDate>());
		}
		LocalDate date = SODDateUtils.floorToLastMonday(candidateKey.date);
		dates.add(date);
		
		Pairing p = getCache(candidateKey).get(candidate);
		Collection<Pairing> pairs = pool.get(candidateKey);
		pairs.remove(p);
		var labeledPair = p.label(label); 
		pairs.add(labeledPair);
		
		//System.err.println("Labeling:\t" + candidateKey.toStringPretty() + "\t" + labeledPair + "\t" + System.identityHashCode(labeledPair) + "\t" + System.identityHashCode(p));
	}

	public static Map<DatedSession, Student> buildERSchedule(GroupPractice practice, Map<DatedSession, Collection<Pairing>> global, Predicate<Student> excludedStudents, Function<DatedSession, UpperLower> getPriority) {
		return buildERScheduler(practice, global, (x, y) -> true, excludedStudents, getPriority).schedule();
	}
	
	public static ERNPEScheduler buildERScheduler(GroupPractice practice, Map<DatedSession, Collection<Pairing>> global, BiPredicate<DatedSession, Student> moratoriumFilter, Predicate<Student> excludedStudents, Function<DatedSession, UpperLower> getPriority) {
		return new ERNPEScheduler(practice, global, ER_LABEL, PairingSelectionMode.DEFAULT, ER_MODE_TOGGLE, moratoriumFilter, excludedStudents, getPriority);
	}

	public static Map<DatedSession, Student> buildNPESchedule(GroupPractice practice, Map<DatedSession, Collection<Pairing>> global, Predicate<Student> excludedStudents, Function<DatedSession, UpperLower> getPriority) {
		return buildNPEScheduler(practice, global, (x, y) -> true, excludedStudents, getPriority).schedule();
	}
	
	public static ERNPEScheduler buildNPEScheduler(GroupPractice practice, Map<DatedSession, Collection<Pairing>> global, BiPredicate<DatedSession, Student> moratoriumFilter, Predicate<Student> excludedStudents, Function<DatedSession, UpperLower> getPriority) {
		return new ERNPEScheduler(practice, global, NPE_LABEL, PairingSelectionMode.PRIMARY, NPE_MODE_TOGGLE, moratoriumFilter, excludedStudents, getPriority);
	}
}
