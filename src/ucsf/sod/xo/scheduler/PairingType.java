package ucsf.sod.xo.scheduler;

import java.util.Comparator;
import java.util.Map;

public enum PairingType {
	PRIMARY('P'),
	SECONDARY_4('4'),
	SECONDARY_3('3'),
	PAIRED('p'),
	SEPARATED('S'),
	SECOND_42('2'),
	SECOND_32('@'),
	ORPHAN('O');
	
	public final char symbol;
	PairingType(char symbol) {
		this.symbol = symbol;
	}
	
	public static Comparator<PairingType> d2Priority = new Comparator<PairingType>() {
		
		private Map<PairingType, Integer> priority = Map.ofEntries(
			Map.entry(PairingType.SECOND_42, 0),
			Map.entry(PairingType.SECOND_32, 10),
			Map.entry(PairingType.PRIMARY, 20),
			Map.entry(PairingType.SECONDARY_4, 30),
			Map.entry(PairingType.SECONDARY_3, 40),
			Map.entry(PairingType.PAIRED, 50),
			Map.entry(PairingType.SEPARATED, 50),
			Map.entry(PairingType.ORPHAN, 99)
		);
		
		@Override
		public int compare(PairingType o1, PairingType o2) {
			return Integer.compare(priority.get(o1), priority.get(o2));
		}
	};
}