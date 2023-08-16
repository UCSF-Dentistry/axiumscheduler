package ucsf.sod.xo.objects;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum GroupPractice {

	A,
	B,
	C,
	D,
	E,
	F;
	
	private final Set<Student> students = new HashSet<Student>();
	private final Map<Character, Set<Student>> pods = new HashMap<Character, Set<Student>>();
	
	GroupPractice() {
		for(char c : new char[] { '1', '2', '3', '4', '5' }) {
			pods.put(c, new HashSet<Student>());
		}
	}
			
	public void register(Student s, char pod) {
		students.add(s);
		pods.get(pod).add(s);
	}
	
	public int studentCount() {
		return students.size();
	}
	
	public Set<Student> getAllStudents() {
		return Collections.unmodifiableSet(students);
	}
	
	private static Predicate<Student> IS_CLINIC_STUDENT = s -> (s.isD3() || s.isD4() || s.isID3() || s.isID4());
	public Set<Student> getClinicStudents() {
		return getStudentsStream(IS_CLINIC_STUDENT).collect(Collectors.toCollection(TreeSet::new));
	}
	
	public Stream<Student> getStudentsStream() {
		return getStudentsStream(s -> true);
	}
	
	public Stream<Student> getStudentsStream(Predicate<Student> p) {
		return students.stream().filter(p);
	}
}