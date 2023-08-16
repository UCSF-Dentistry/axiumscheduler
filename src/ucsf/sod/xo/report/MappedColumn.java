package ucsf.sod.xo.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import ucsf.sod.util.SODExcelFactory;

public class MappedColumn<T, K, V, S> extends MappedColumnGeneric<T, Map<K, V>, S> {

	public final List<K> keysToQuery;
	public final Function<V, S> consumer;
	public final V defaultValue;

	private MappedColumn(List<String> columnNames, Function<T, Map<K, V>> mapper, List<K> keysToQuery, Function<V, S> consumer, V defaultValue) {
		super(columnNames, mapper, List.of());
		this.keysToQuery = new ArrayList<K>(keysToQuery);
		this.consumer = consumer;
		this.defaultValue = defaultValue;
	}
	
	/**
	 * Generates cells to produce the main data. Assumes given row is fully packed (i.e. no skipped cells)
	 * @param r the row to put header cells into
	 */
	@Override
	public void generateData(SODExcelFactory s, T t) {
		Map<K, V> v = mapper.apply(t);
		for(K key : keysToQuery) {
			s.createCell(consumer.apply(v.getOrDefault(key, defaultValue)));
		}
	}
	
	public static <T, K, V> MappedColumn<T, K, V, String> of(Function<T, Map<K, V>> mapper, List<K> keysToQuery, Function<V, String> consumer, V defaultValue) {
		return new MappedColumn<T, K, V, String>(keysToQuery.stream().collect(Collectors.mapping(Object::toString, Collectors.toList())), mapper, keysToQuery, consumer, defaultValue);
	}
}