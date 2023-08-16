package ucsf.sod.xo.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import ucsf.sod.xo.XOGridUtils;

public class CapacityScenario {

	public final int maxCapacity;
	private final List<Pairing> pairs;
	private final Map<PairingType, Integer> pairings;
	
	private CapacityScenario(int maxCapacity, List<Pairing> pairs) {
		this(
			maxCapacity, 
			pairs,
			pairs.stream().collect(Collectors.groupingBy(
				p -> p.type, 
				() -> new TreeMap<PairingType, Integer>(), 
				Collectors.collectingAndThen(Collectors.counting(), l -> l.intValue())
			))
		);
	}

	private CapacityScenario(int maxCapacity, List<Pairing> pairs, Map<PairingType, Integer> pairings) {
		this.maxCapacity = maxCapacity;
		this.pairs = List.copyOf(pairs);
		this.pairings = pairings;
	}
	
	/**
	 * 
	 * @return a new list containing the pairs
	 */
	public List<Pairing> getPairs() {
		return new ArrayList<Pairing>(pairs);
	}
	
	public int getChairsOccupied() {
		return pairs.size();
	}
	
	public int getAssistantCount() {
		return getChairsOccupied() - pairings.getOrDefault(PairingType.SEPARATED, 0) - pairings.getOrDefault(PairingType.ORPHAN, 0);
	}
	
	public boolean hasSplittablePairs() {
		boolean canSplit = true;
		
		int secondary4 = pairings.getOrDefault(PairingType.SECONDARY_4, 0);
		int secondary3 = pairings.getOrDefault(PairingType.SECONDARY_3, 0);
		int total = secondary4 + secondary3;
		if(total > 0) {
			canSplit = canSplit(1);
		}
		return total != 0 && canSplit;
	}
	
	public List<Pairing> getSplittablePairs() {
		return pairs.stream().filter(p -> p.type == PairingType.SECONDARY_4 || p.type == PairingType.SECONDARY_3).collect(Collectors.toList());
	} 
	
	private List<Pairing> getSplittableLinks() {
		return pairs.stream().filter(p -> p.type == PairingType.PRIMARY).collect(Collectors.toList());
	}
	
	public double getPercentHasAssistants() {
		return getAssistantCount() / (double)getChairsOccupied();
	}
	
	public boolean canSplit(int amountToSplit) {
		return (getAssistantCount() - amountToSplit) / (double)(getChairsOccupied() + amountToSplit) >= 0.375;
	}
	
	public boolean atCapacity() {
		return maxCapacity == getChairsOccupied();
	}
	
	public CapacityScenario splitHalf() {

		if(!hasSplittablePairs()) {
			return this;
		}
		
		int oldSplitCount = getSplittablePairs().size();
		int newSplitCount = oldSplitCount >> 1;
		int newChairCount = oldSplitCount - newSplitCount;
		
		if(newChairCount == 0) {
			throw new RuntimeException("Unable to split");
		} else {
			return splitPairs(newChairCount);
		}
	}
	
	public CapacityScenario splitPairs(int numberToSplit) {
		return splitPairs(numberToSplit, false);
	}
	
	public CapacityScenario splitPairs(int numberToSplit, boolean force) {
		
		if(numberToSplit <= 0) {
			System.err.println("This should be called with at least 1 pair to split: " + numberToSplit);
			return this;
		}
		
		int chairsOccupied = getChairsOccupied();
		if(!force && (chairsOccupied + numberToSplit) > maxCapacity) {
			int chairsToFill = maxCapacity - chairsOccupied;
			System.err.println("Proposed split [" + numberToSplit + "], Actual need ["+chairsToFill+"]");
			numberToSplit = chairsToFill;
		}

		List<Pairing> splittablePairs = getSplittablePairs();
		if(force && splittablePairs.size() < numberToSplit) {
			numberToSplit = splittablePairs.size();
		} else if(!canSplit(numberToSplit)) {
			return splitPairs(numberToSplit-1);
		}
		
		XOGridUtils.shuffle(splittablePairs);
		return split(List.copyOf(splittablePairs.subList(0, numberToSplit)));
	}
	
	public CapacityScenario splitLinksToCapacity() {
		int need = maxCapacity - getChairsOccupied();
		int ask = need;
		while(ask > 0 && !canSplit(ask)) {
			ask--;
		}
		
		if(ask == 0) {
			System.err.println("Cannot split below threshold of 3:8 ratio");
			return this;
		} else if(ask != need) {
			System.err.println("Needed to split "+need+", able to split " + ask);
		}
		
		return splitLinks(ask);
	}
	
	public CapacityScenario splitLinks(int numberToSplit) {
		List<Pairing> splittablePairs = getSplittableLinks();
		if(splittablePairs.size() > 0) {
			XOGridUtils.shuffle(splittablePairs);
			return split(List.copyOf(splittablePairs.subList(0, numberToSplit)));
		} else {
			return this;
		}
	}
	
	private CapacityScenario split(List<Pairing> pairsToSplit) {
		List<Pairing> postSplit = new ArrayList<Pairing>(pairs);
		for(Pairing p : pairsToSplit) {
			if(!postSplit.remove(p)) {
				throw new RuntimeException("Unable to remove from list: " + p);
			}
			
			postSplit.addAll(p.split());
		}
		return new CapacityScenario(maxCapacity, postSplit);
	}
	
	public CapacityScenario exceedCapacity(int targetCapacity) {
		if(targetCapacity < 0) {
			throw new IllegalArgumentException("Given target capacity is less than 0: " + targetCapacity);
		}
		
		int need = targetCapacity - getChairsOccupied();
		while(need > 0 && !canSplit(need)) {
			need--;
		}
		
		if(need > 0) {
			CapacityScenario newScenario;
			if(hasSplittablePairs()) {
				newScenario = splitPairs(need, true);
			} else {
				newScenario = splitLinks(need);
			}

			return newScenario;
		} else {
			return this;
		}
	}
	
	public static enum ScenarioMode {
		CLINIC_BREAK,
		REDUCED_CAPCITY,
		BELOW_CAPCITY,
		MAXIMIZE,
		SUFFICIENT;
	}
	
	public static CapacityScenario of(int chairCapacity, List<Pairing> pairs, Function<CapacityScenario, ScenarioMode> scenarioSupplier) {
		CapacityScenario scenario = new CapacityScenario(chairCapacity, pairs);
		ScenarioMode mode = scenarioSupplier.apply(scenario);
		switch(mode) {
			case CLINIC_BREAK:
			case REDUCED_CAPCITY:
			case SUFFICIENT:
				return scenario;
			case BELOW_CAPCITY:
				CapacityScenario newScenario = scenario;
				do {
					newScenario = newScenario.splitHalf();
				} while(newScenario.getChairsOccupied() < chairCapacity && newScenario.hasSplittablePairs());
				
				if(newScenario.getChairsOccupied() < chairCapacity) {
					CapacityScenario linkSplit = newScenario.splitLinksToCapacity();
					if(linkSplit != newScenario) {
						newScenario = linkSplit;
					}
				}
				
				return newScenario;
			case MAXIMIZE:
				return scenario.splitPairs(pairs.size(), true);
			default:
				throw new RuntimeException("Unknown scenario mode: " + mode);
		}
	}
}