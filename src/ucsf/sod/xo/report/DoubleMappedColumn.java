package ucsf.sod.xo.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import ucsf.sod.util.SODExcelFactory;

public class DoubleMappedColumn<T, K, K2, V2, S> extends MappedColumnGeneric<T, Map<K, Map<K2, V2>>, S> {

	public final List<K> keysToQuery;
	public final List<K2> keysToQuery2;
	public final Function<V2, S> consumer;
	public final V2 defaultValue;

	private DoubleMappedColumn(List<String> columnNames, Function<T, Map<K, Map<K2, V2>>> mapper, List<K> keysToQuery, List<K2> keysToQuery2, Function<V2, S> consumer, V2 defaultValue) {
		super(columnNames, mapper, List.of());
		this.keysToQuery = new ArrayList<K>(keysToQuery);
		this.keysToQuery2 = new ArrayList<K2>(keysToQuery2);
		this.consumer = consumer;
		this.defaultValue = defaultValue;
	}

	@Override
	public void generateHeader(SODExcelFactory s) {
		for(K k : keysToQuery) {
			for(K2 name : keysToQuery2) {
				s.createCell(name.toString());
			}
		}
	}

	/**
	 * Generates cells to produce the main data. Assumes given row is fully packed (i.e. no skipped cells)
	 * @param r the row to put header cells into
	 */
	@Override
	public void generateData(SODExcelFactory s, T t) {
		Map<K, Map<K2, V2>> v = mapper.apply(t);
		for(K key : keysToQuery) {
			var vv = v.getOrDefault(key, Map.of());
			for(K2 key2 : keysToQuery2) {
				s.createCell(consumer.apply(vv.getOrDefault(key2, defaultValue)));
			}
		}
	}
	
	public static <T, K, K2, V2> DoubleMappedColumn<T, K, K2, V2, String> of(Function<T, Map<K, Map<K2, V2>>> mapper, List<K> keysToQuery, List<K2> keysToQuery2, Function<V2, String> consumer, V2 defaultValue) {
		return new DoubleMappedColumn<T, K, K2, V2, String>(keysToQuery.stream().collect(Collectors.mapping(Object::toString, Collectors.toList())), mapper, keysToQuery, keysToQuery2, consumer, defaultValue);
	}
}