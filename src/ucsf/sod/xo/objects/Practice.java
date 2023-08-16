package ucsf.sod.xo.objects;

public enum Practice {
	UCSF_DENTAL(1, "UCSF Dental Center", PracticeType.OTHER),
	PARNASSUS(5, "Parnassus", PracticeType.PRIMARY),
	BUCHANAN(6, "Buchanan", PracticeType.PRIMARY),
	RESEARCH(7, "Clinical Gen Research", PracticeType.OTHER),
	PEDS(8, "Peds", PracticeType.OTHER),
	PERIO(9, "Perio", PracticeType.PRIMARY),
	FGP(12, "Faculty Group Practice", PracticeType.OTHER),
	PROS(13, "Pros", PracticeType.OTHER),
	ORTHO(15, "Ortho", PracticeType.OTHER),
	PEDS_ORTHO(17, "Peds Ortho", PracticeType.OTHER),
	REDDING(21, "Redding", PracticeType.EXTERNSHIP),
	LA_CLINICA(29, "La Clinica (Oakland Transit Village)", PracticeType.EXTERNSHIP),
	ASIAN_HEALTH(31, "Asian Health", PracticeType.EXTERNSHIP),
	MARIN_COMMUNITY(32, "Marin Community", PracticeType.EXTERNSHIP),
	OROVILLE(35, "Oroville", PracticeType.EXTERNSHIP),
	PEDS_OR(40, "Peds OR", PracticeType.OTHER),
	GARDNER(44, "Gardner", PracticeType.EXTERNSHIP),
	SACRAMENTO(48, "Sacramento", PracticeType.EXTERNSHIP),
	SHASTA(53, "Shasta", PracticeType.EXTERNSHIP),
	BERKELEY(58, "Lifelong Berkeley", PracticeType.EXTERNSHIP),
	YUBA(62, "Yuba", PracticeType.EXTERNSHIP),
	SAN_MATEO(63, "San Mateo", PracticeType.EXTERNSHIP),
	ASIAN_HEALTH_11TH(64, "Asian Health - 11th Street", PracticeType.EXTERNSHIP),
	ENDO_RESIDENT(65, "PG Endo", PracticeType.OTHER),
	ENDO_FACULTY(66, "Faculty Endo", PracticeType.OTHER),
	AEGD(67, "AEGD", PracticeType.OTHER),
	PEDS_MB(68, "Peds at Mission Bay", PracticeType.OTHER),
	ORAL_SURGERY(69, "Oral Surgery", PracticeType.OTHER),
	ONCOLOGY(70, "Dental Oncology", PracticeType.OTHER),
	COLUSA(71, "Colusa", PracticeType.EXTERNSHIP),
	WEST_OAKLAND(73, "West Oakland", PracticeType.EXTERNSHIP),
	EAST_OAKLAND(74, "East Oakland", PracticeType.EXTERNSHIP),
	ALAMEDA(75, "Alameda", PracticeType.EXTERNSHIP),
	YUKON(76, "Yukon", PracticeType.EXTERNSHIP),
	GARDNER_SOUTH(77, "Gardner South County", PracticeType.EXTERNSHIP),
	HIGHLAND(78, "Highland Hospital", PracticeType.EXTERNSHIP),
	UNKNOWN(-1, "UNKNOWN", PracticeType.OTHER);
	
	public final int code;
	public final String practiceName;
	public final PracticeType type;
	Practice(int code, String name, PracticeType type) {
		this.code = code;
		this.practiceName = name;
		this.type = type;
	}
	
	public static Practice toPractice(int c) {
		for(Practice p : values()) {
			if(c == p.code) {
				return p;
			}
		}
		
		throw new RuntimeException("Unable to find practice: " + c);
	}
	
	public static enum PracticeType {
		PRIMARY,
		EXTERNSHIP,
		OTHER;
	}
}

