package ucsf.sod.xo.report;

import java.util.List;
import java.util.function.Function;

import ucsf.sod.util.SODExcelFactory;

public class MappedColumnGeneric<T, V, S> extends Column<T, S> {

	public final Function<T, V> mapper;
	public final List<Function<V, String>> consumers;
	
	public MappedColumnGeneric(List<String> columnNames, Function<T, V> mapper, List<Function<V, String>> mappedConsumers) {
		super(columnNames, List.of());
		this.mapper = mapper;
		this.consumers = mappedConsumers;
	}
	
	/**
	 * Generates cells to produce the main data. Assumes given row is fully packed (i.e. no skipped cells)
	 * @param r the row to put header cells into
	 */
	@Override
	public void generateData(SODExcelFactory s, T t) {
		V v = mapper.apply(t);
		for(Function<V, String> consumer : consumers) {
			s.createCell(consumer.apply(v));				
		}
	}
	
	public static <T, V> MappedColumnGeneric<T, V, String> of(List<String> columnNames, Function<T, V> mapper, List<Function<V, String>> mappedConsumers) {
		return new MappedColumnGeneric<T, V, String>(columnNames, mapper, mappedConsumers);
	}
}