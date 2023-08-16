package ucsf.sod.xo.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ucsf.sod.xo.objects.Student;
import ucsf.sod.xo.objects.Student.UpperLower;

public class Pairing {

	public final Student a;
	public final Student b;
	public final char pod;
	public final PairingType type;
	private final List<Pairing> history; // TODO: maintain this history
	public final String label;
	@Deprecated private Student priorityStudent = null;

	private Pairing(Student a, Student b, PairingType type, List<Pairing> sources) {
		this(a, b, type, sources, null);
	}

	private Pairing(Student a, Student b, PairingType type, List<Pairing> sources, String label) {
		this.a = a;
		this.b = b;
		this.pod = (a == Student.PLACEHOLDER ? b.pod : a.pod);
		this.type = type;
		this.history = sources;
		this.label = label;
	}
	
	public List<Pairing> split() {
		if(type == PairingType.SEPARATED || type == PairingType.ORPHAN) {
			throw new RuntimeException("Cannot split a pair that is already SEPARATED or ORPHANed.");
		} else {
			return List.of(
				new Pairing(this.a, Student.PLACEHOLDER, PairingType.SEPARATED, List.of(this)),
				new Pairing(Student.PLACEHOLDER, this.b, PairingType.SEPARATED, List.of(this))
			);
		}
	}
	
	public Pairing strip(Student s) {
		if(this.a == s) {
			return new Pairing(this.b, Student.PLACEHOLDER, PairingType.SEPARATED, List.of(this));
		} else if(this.b == s) {
			return new Pairing(this.a, Student.PLACEHOLDER, PairingType.SEPARATED, List.of(this));
		}
		
		throw new RuntimeException("Student ["+s.id+"] does not exist in this pairing: " + this);
	}
	
	public Pairing label(String label) {
		if(this.label != null) {
			System.err.println("Pairing already has a pre-existing label");
		}
		return new Pairing(this.a, this.b, this.type, List.of(this), label);
	}
	
	public Pairing pair(Pairing p) {
		if(type != PairingType.ORPHAN && type != PairingType.SEPARATED) {
			throw new IllegalStateException("pair called on a pair that is not ORPHAN nor SEPARATED: " + type);
		} else if(p.type != PairingType.ORPHAN && p.type != PairingType.SEPARATED) {
			throw new IllegalArgumentException("Given pairing is not ORPHAN nor SEPARATED: " + p.type);
		}
		
		return new Pairing(getSoloStudent(), p.getSoloStudent(), PairingType.PAIRED, List.of(this, p));
	}
	
	public Pairing correctType() {
		if(type != PairingType.ORPHAN) {
			throw new IllegalArgumentException("Given pairing is not ORPHAN: " + type);
		}
		
		Student s = getSoloStudent();
		if(s.getPrimaryLink() == Student.PLACEHOLDER) {
			return new Pairing(a, b, PairingType.PRIMARY, List.of(this), label);
		} else if(s.getPartner() == Student.PLACEHOLDER) {
			if(s.isD3() || s.isID3()) {
				return new Pairing(a, b, PairingType.SECONDARY_3, List.of(this), label);
			} else if(s.isD4() || s.isID4()){
				return new Pairing(a, b, PairingType.SECONDARY_4, List.of(this), label);
			} else {
				throw new RuntimeException("Correcting a pairing with a student that is neither I/D3 or I/D4: " + this);
			}
		} else {
			throw new RuntimeException("Something went horribly wrong in Pairing correction");
		}
	}
	
	public Student getSoloStudent() {
		if(type != PairingType.ORPHAN && type != PairingType.SEPARATED)
			throw new RuntimeException("Pairing is not of type ORPHAN nor SEPARATED: " + type);
		
		return a == Student.PLACEHOLDER ? b : a;
	}
	
	public boolean isSoloPairing() {
		return (a == Student.PLACEHOLDER && b != Student.PLACEHOLDER) || (b == Student.PLACEHOLDER && a != Student.PLACEHOLDER);
	}
	
	public boolean isPrimaryPairing() {
		return type == PairingType.PRIMARY;
	}
	
	@Deprecated
	public boolean hasPriorityStudent() {
		return priorityStudent != null;
	}
	
	@Deprecated
	public void setPriorityStudent(Student s) {
		if(a != s && b != s) {
			throw new IllegalArgumentException("Student " + s.id + " is not part of this pairing");
		}
		
		priorityStudent = s;
	}

	@Deprecated
	public Student getPriorityStudent() {
		return priorityStudent;
	}

	@Override
	public String toString() {
		return new StringBuilder()
			.append("Pairing{")
			.append("a=").append(a.id).append('(').append(a.first).append('|').append(a.pod + "" + (a.cluster == null ? "" : a.cluster.symbol)).append('|').append(a.priority.toChar()).append(')').append(',')
			.append("b=").append(b.id).append('(').append(b.first).append('|').append(b.pod + "" + (b.cluster == null ? "" : b.cluster.symbol)).append('|').append(a.priority.toChar()).append(')').append(',')
			.append("type=").append(type).append(',')
			.append("label=").append(label == null ? "" : label)
			.append('}')
			.toString();
	}
	
	static Pairing pairUp(Pairing a, Pairing b) {
		if(a.type != PairingType.SEPARATED || a.type != PairingType.ORPHAN) {
			throw new IllegalArgumentException("Pairing is not of type SEPARATED or ORPHAN: " + a);
		} else if(b.type != PairingType.SEPARATED || b.type != PairingType.ORPHAN) {
			throw new IllegalArgumentException("Pairing is not of type SEPARATED or ORPHAN: " + b);				
		}
		
		Student _a = a.a;
		if(_a == Student.PLACEHOLDER) {
			_a = a.b;
		}
		
		Student _b = b.a;
		if(_b == Student.PLACEHOLDER) {
			_b = b.b;
		}
		
		return new Pairing(_a, _b, PairingType.PAIRED, List.of(a, b));
	}
	
	public static Pairing of(Student a, Student b, PairingType type) {
		switch(type) {
		case PRIMARY:
			if(a.isD4() || a.isID4()) {
				return new Pairing(a, b, type, List.of());
			} else if(b == Student.PLACEHOLDER) {
				if(a.priority == UpperLower.UPPER) {
					return new Pairing(a, b, type, List.of());
				} else {
					return new Pairing(b, a, type, List.of());
				}
			} else {
				return of(b, a, type);
			}
		case SECONDARY_3:
		case SECONDARY_4:
			if(a.priority == UpperLower.UPPER) {
				return new Pairing(a, b, type, List.of());
			} else {
				return of(b, a, type);
			}
		case SEPARATED:
		case ORPHAN:
			if(a.priority == UpperLower.UPPER) {
				return new Pairing(a, b, type, List.of());
			} else {
				return new Pairing(b, a, type, List.of());
			}
		case SECOND_42:
		case SECOND_32:
			if(!b.isD2()) {
				throw new RuntimeException("Pairing should only be created with a D2 as secondary");
			}
			
			return new Pairing(a, b, type, List.of());
		default:
			throw new RuntimeException("Unknown type: " + type);
		}
	}
	
	public static List<Pairing> pairUp(Collection<Student> s) {
		List<Pairing> l = new ArrayList<Pairing>();
		Set<Student> unprocessed = new HashSet<Student>(s);
		for(Student student : s) {

			// Continue to next student if student has already been processed, or is a D2
			if(student.isD2() || !unprocessed.contains(student)) {
				continue;
			}

			// Student paired with D2 link
			Student candidate = student.getSecondaryLink();
			if(unprocessed.contains(candidate)) {
				if(student.isFourthYear()) {
					
					// Check the third-year
					Student thirdYear = student.getPrimaryLink();
					if(thirdYear == Student.PLACEHOLDER) {
						System.err.println("Student doesn't have a link: " + student.id);
					} else if(unprocessed.contains(thirdYear)) {
						//Let's pair the two D3s together
						Student thirdYearPartner = thirdYear.getPartner();
						if(unprocessed.contains(thirdYearPartner)) {
							l.add(Pairing.of(thirdYear, thirdYearPartner, PairingType.SECONDARY_3));
							unprocessed.remove(thirdYear);
							unprocessed.remove(thirdYearPartner);
						} else {
							System.err.println("Third-year partner ["+thirdYear.id+"] is missing; could partner be on rotation? Making third-year an orphan");
							l.add(Pairing.of(thirdYear, Student.PLACEHOLDER, PairingType.ORPHAN));
							unprocessed.remove(thirdYear);
						}
						
					// Check the third-year partner
					} else if(unprocessed.contains(thirdYear.getPartner())) {
						System.err.println("Third-year ["+thirdYear.id+"] is missing, but partner is around. Could third-year be on rotation?");
					}
					
					l.add(Pairing.of(student, candidate, PairingType.SECOND_42));
					unprocessed.remove(student);
					unprocessed.remove(candidate);

				} else if(student.isThirdYear()) {
					
					// Check the fourth-year; if present, connect the D2 with the D4
					Student fourthYear = student.getPrimaryLink();
					if(unprocessed.contains(fourthYear)) {
						
						l.add(Pairing.of(fourthYear, candidate, PairingType.SECOND_42));
						unprocessed.remove(fourthYear);
						unprocessed.remove(candidate);
						
						Student partner = student.getPartner();
						if(!unprocessed.contains(partner)) {
							throw new RuntimeException("Where is the partner ["+partner.id+"] of " + student.id);
						}
						
						l.add(Pairing.of(student, partner, PairingType.SECONDARY_3));
						unprocessed.remove(student);
						unprocessed.remove(partner);						
						
					// Fourth-year is not around
					} else {
						l.add(Pairing.of(student, candidate, PairingType.SECOND_32));
						unprocessed.remove(student);
						unprocessed.remove(candidate);
					}
				} else {
					throw new RuntimeException("Not sure how we failed the fourth-year and third-year tests: " + student.id);
				}
				
				continue;
			}

			// Student paired with link
			candidate = student.getPrimaryLink();
			if(unprocessed.contains(candidate)) {
				l.add(Pairing.of(student, candidate, PairingType.PRIMARY));
				unprocessed.remove(student);
				unprocessed.remove(candidate);
				continue;
			}
			
			// Student paired with clinic partner
			candidate = student.getPartner();
			if(unprocessed.contains(candidate)) {
				if(unprocessed.contains(candidate.getPrimaryLink())) {
					l.add(Pairing.of(candidate, candidate.getPrimaryLink(), PairingType.PRIMARY));
					unprocessed.remove(candidate);
					unprocessed.remove(candidate.getPrimaryLink());
					
					PairingType type;
					if(student.getPrimaryLink() == Student.PLACEHOLDER) {
						type = PairingType.PRIMARY;
						System.err.println(student.id + " pairing upgraded to PRIMARY from ORPHAN");
					} else {
						type = PairingType.ORPHAN;
					}
					l.add(Pairing.of(student, Student.PLACEHOLDER, type));
					unprocessed.remove(student);
				} else {
					PairingType type;
					if(student.isD3() || student.isID3()) {
						type = PairingType.SECONDARY_3;
					} else if(student.isD4() || student.isID4()) {
						type = PairingType.SECONDARY_4;
					} else {
						throw new RuntimeException("Unknown student year: " + student);
					}
					
					l.add(Pairing.of(student, candidate, type));
					unprocessed.remove(student);
					unprocessed.remove(candidate);
				}
				continue;
			}
			
			// No link, no partner; if partner doesn't exist, must use link's partner to check "cross"
			if(candidate == Student.PLACEHOLDER) {
				candidate = student.getPrimaryLink();
				if(candidate == Student.PLACEHOLDER) {
					//throw new RuntimeException("Unable to find cross for " + student.id);
					l.add(Pairing.of(student, Student.PLACEHOLDER, PairingType.ORPHAN));
					unprocessed.remove(student);
					continue;
				}
				
				// "cross" located and accounted for, since "cross"'s link is placeholder 
				Student cross = candidate.getPartner();
				if(unprocessed.contains(cross)) {
					System.err.println(cross.id + " pairing upgraded to PRIMARY from ORPHAN");
					l.add(Pairing.of(cross, Student.PLACEHOLDER, PairingType.PRIMARY));
					unprocessed.remove(cross);
				}
				
				// student is truly orphaned
				l.add(Pairing.of(student, Student.PLACEHOLDER, PairingType.ORPHAN));
				unprocessed.remove(student);
				
			// Partner is a person who is not around for some reason
			} else {
				Student cross = candidate.getPrimaryLink();
				if(cross == Student.PLACEHOLDER) {
					// if "cross" is placeholder, check why we couldn't find primary link; could it because primary link is a placeholder?
					if(student.getPrimaryLink() == Student.PLACEHOLDER) {
						System.err.println("Cross and link are PLACEHOLDERS for "+student.id+"; maybe clinic partner is out on rotation?");
						l.add(Pairing.of(student, Student.PLACEHOLDER, PairingType.ORPHAN));
						unprocessed.remove(student);

					// because link and partner are people, student truly orphaned
					} else {
						l.add(Pairing.of(student, Student.PLACEHOLDER, PairingType.ORPHAN));
						unprocessed.remove(student);
					}
				} else {
					// if "cross" is present, because we didn't find cross's link/student's partner, cross is an orphan
					// we would not reach this logic point if student's link was present at the beginning of the loop
					if(unprocessed.contains(cross)) {
						l.add(Pairing.of(cross, Student.PLACEHOLDER, PairingType.ORPHAN));
						unprocessed.remove(cross);
					}
					
					// "cross" has no impact on student; had link not been a placeholder, we would have finished this logic at the beginning of this loop
					PairingType type;
					if(student.getPrimaryLink() == Student.PLACEHOLDER) {
						type = PairingType.PRIMARY;
						System.err.println(student.id + " pairing upgraded to PRIMARY from ORPHAN");
					} else {
						type = PairingType.ORPHAN;
					}
					l.add(Pairing.of(student, Student.PLACEHOLDER, type));						
					unprocessed.remove(student);
				}
			}
		}
		
		if(unprocessed.size() != 0) {
			throw new RuntimeException("A student has not been paired: " + unprocessed.stream().collect(Collectors.mapping(student -> student.id, Collectors.joining(","))));
		}
		
		return l;
	}
}