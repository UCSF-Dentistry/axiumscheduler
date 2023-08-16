package ucsf.sod.xo.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import ucsf.sod.xo.XOGridUtils;

public interface Scheduler<K, T, V> {

	public static enum Halt {
		CONTINUE,	// state adjusted and continue 
		RESET, 		// reset as if starting fresh
		HALT; 		// just stop
	}
	
	public List<K> getCandidateKeys();
	public List<V> getCandidateValues();
	public Map<K, Collection<T>> getSpace();
	
	/**
	 * Get an empty map
	 * @return returns an empty map
	 */
	public default Map<K, V> getMapInstance() {
		return new TreeMap<K, V>();
	}
	
	public List<K> getUnassignedKeys();
	
	/**
	 * Setter to allow returning of keys that are unassigned via the getUnassignedKeys();
	 * Should only be called by Scheduler
	 */
	public void setUnassignedKeys(List<K> l);

	public Map<V, Integer> getRemainingMap();
	
	public default void recordRemaining(List<V> l) {
		Map<V, Integer> remain = getRemainingMap();
		for(V v : l) {
			int i = remain.getOrDefault(v, 0);
			remain.put(v, i+1);
		}
	}
	
	public default Map<K, V> schedule() {
		Map<K, V> pairings = new TreeMap<K, V>();
		
		List<K> unassignedKeys = getCandidateKeys();
		Map<K, Collection<T>> space = getSpace();
		
		List<K> shuffleList = unassignedKeys;
		List<V> processingBlock = getCandidateValues();
		while(!unassignedKeys.isEmpty()) {
			
			// Take the unassigned keys and re-shuffle, try to slot the remaining
			shuffleList = new ArrayList<K>(unassignedKeys);
			XOGridUtils.shuffle(shuffleList);			

			var results = join_iter(
				processingBlock,
				shuffleList,
				space
			);
			pairings.putAll(results);
			unassignedKeys.removeAll(results.keySet());

			if(results.size() == 0) {
				var _result = handleNoResults(unassignedKeys);
				if(_result == Halt.HALT) {
					setUnassignedKeys(unassignedKeys);
					break;
				} else if(_result == Halt.RESET) {
					processingBlock = getCandidateValues();
				}
			} else if(results.size() != processingBlock.size()) {
				List<V> remaining = new ArrayList<V>(processingBlock);
				remaining.removeAll(results.values());
				recordRemaining(remaining);
				handlePartialResults(results, remaining, unassignedKeys);
				processingBlock = getCandidateValues();
			} else {
				handleFullResults(results, unassignedKeys);
				processingBlock = getCandidateValues();
			}
			
			//results.forEach((k, v) -> System.err.println(k + "\t" + v));
		}
		
		return pairings;
	}
	
	public default Map<K, V> schedule_recursive() {
		
		Map<K, V> pairings = new TreeMap<K, V>();
		
		List<K> unassignedKeys = getCandidateKeys();
		Map<K, Collection<T>> space = getSpace();
		
		List<V> processingBlock = getCandidateValues();
		while(!unassignedKeys.isEmpty()) {
			var results = join(
				processingBlock,
				new ArrayList<K>(unassignedKeys),
				space
			);
			pairings.putAll(results);
			unassignedKeys.removeAll(results.keySet());

			if(results.size() == 0) {
				var _result = handleNoResults(unassignedKeys);
				if(_result == Halt.HALT) {
					setUnassignedKeys(unassignedKeys);
					break;
				} else if(_result == Halt.RESET) {
					processingBlock = getCandidateValues();
				}
			} else if(results.size() != processingBlock.size()) {
				List<V> remaining = new ArrayList<V>(processingBlock);
				remaining.removeAll(results.values());
				handlePartialResults(results, remaining, unassignedKeys);
				processingBlock = remaining;
			} else {
				handleFullResults(results, unassignedKeys);
				processingBlock = getCandidateValues();
			}
		}
		
		return pairings;
	}
	
	/*
	 * Returns a map with a subset of keys mapped to non-null values
	 */
	public default Map<K, V> join_iter(List<V> values, List<K> keys, Map<K, Collection<T>> space) {
		
		// Duplicate because we are going to take sublists of them
		List<K> subKeys = new ArrayList<K>(keys);
		List<V> subValues  = new ArrayList<V>(values);
		
		Map<K, V> m = getMapInstance();

		// m.size() is 0 and grows to be the values.size()
		while(subKeys.size() != 0 && subValues.size() != 0 && m.size() != values.size()) {
			
			K candidateKey = subKeys.remove(0);

			// Determine the options
			List<V> candidates = filterCandidates(candidateKey, new ArrayList<V>(subValues), space.get(candidateKey));
			if(candidates.size() != 0) {
				V candidate = candidates.get(0);
				subValues.remove(candidate);
				insert(m, candidateKey, candidate);
			} else {
				m.put(candidateKey, null);
			}
		}

		m = m.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
//		if(m.size() < values.size() && m.size() != keys.size()) {
//			System.err.println("Couldn't pair the following values: "); 
//			for(V v : subValues) {
//				System.err.println(v);
//			}
//			System.err.println("asdf");
//		}
		
		return m;
	}
	
	public default Map<K, V> join(List<V> values, List<K> keys, Map<K, Collection<T>> space) {

		// Duplicate because we are going to take sublists of them
		List<K> subKeys = new ArrayList<K>(keys);
		List<V> subValues  = new ArrayList<V>(values);
		
		K candidateKey = null;
		V candidate = null;
		while(!subKeys.isEmpty()) {
			
			// Select a key
			candidateKey = subKeys.remove(0);

			// Determine the options
			List<V> candidates = filterCandidates(candidateKey, new ArrayList<V>(subValues), space.get(candidateKey));
			
			// Pick an option
			if(candidates.size() != 0) {
				candidate = candidates.remove(0);
				break;
			}
		}

		if(candidate == null) {
			return Map.of();
		} else {
			Map<K, V> m;
			if(subValues.isEmpty() || subKeys.isEmpty()) {
				m = getMapInstance();
			} else {
				subValues.remove(candidate);
				m = join(subValues, subKeys, space);
				if(m.size() == 0) {
					m = getMapInstance();
				}
			}

			insert(m, candidateKey, candidate);
			return m;
		}
	}
	
	@SuppressWarnings("unlikely-arg-type")
	public default List<V> filterCandidates(K candidateKey, List<V> candidates, Collection<T> options) {
		candidates.removeIf(s -> !options.contains(s));
		XOGridUtils.shuffle(candidates);
		return candidates;
	}
	
	public default void insert(Map<K, V> m, K candidateKey, V candidate) {
		m.put(candidateKey, candidate);
	}
	
	/**
	 * Analyze the outcome of no results to decide whether to continue or stop
	 * @param unassignedKeys The keys still left unassigned
	 * @return false to tell the scheduler to stop; true, to continue
	 */
	public default Halt handleNoResults(List<K> unassignedKeys) {
		reportUnassignedKeys(unassignedKeys);
		return Halt.HALT;
	}
	
	public default void handlePartialResults(Map<K, V> results, List<V> unassignedValues, List<K> unassignedKeys) {
		reportCompletedRound(results, unassignedKeys);
	}
	
	public default void handleFullResults(Map<K, V> results, List<K> unassignedKeys) {
		reportCompletedRound(results, unassignedKeys);
	}
	
	public default void reportUnassignedKeys(List<K> unassignedKeys) {
		System.out.println("The following "+unassignedKeys.size()+" keys remain unassigned: ");
		for(K key : new TreeSet<K>(unassignedKeys)) {
			System.out.println(key);
		}
	}
	
	public default void reportCompletedRound(Map<K, V> results, List<K> unassignedKeys) {
		System.out.println("Made "+results.size()+" assignments, " + unassignedKeys.size() + " key(s) remain");
	}
}
