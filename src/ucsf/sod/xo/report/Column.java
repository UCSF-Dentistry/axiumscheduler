package ucsf.sod.xo.report;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import ucsf.sod.util.SODExcelFactory;

public class Column<T, S> {

	public final List<String> names;
	public final List<Function<T, S>> consumers;

	public Column(String columnNames, Function<T, S> consumer) {
		this.names = List.of(columnNames);
		this.consumers = List.of(consumer);
	}

	public Column(List<String> columnNames, List<Function<T, S>> consumers) {
		this.names = new ArrayList<String>(columnNames);
		this.consumers = new ArrayList<Function<T, S>>(consumers);
	}
	
	/**
	 * Generates cells to produce the header. Assumes given row is fully packed (i.e. no skipped cells)
	 * @param r the row to put header cells into
	 */
	public void generateHeader(SODExcelFactory s) {
		for(String name : names) {
			s.createCell(name);
		}
	}
	
	/**
	 * Generates cells to produce the main data. Assumes given row is fully packed (i.e. no skipped cells)
	 * @param r the row to put header cells into
	 */
	public void generateData(SODExcelFactory s, T t) {
		for(Function<T, S> consumer : consumers) {
			s.createCell(consumer.apply(t));				
		}
	}
	
	public static <T> Column<T, String> emptyColumn() {
		return new Column<T, String>("", s -> "");
	}
	
	public static <T> Column<T, String> of(String columnNames, Function<T, String> consumer) {
		return new Column<T, String>(columnNames, consumer);
	}
	
	public static <T> Column<T, String> of(List<String> columnNames, List<Function<T, String>> consumer) {
		return new Column<T, String>(columnNames, consumer);
	}
	
	public static <T> Column<T, Integer> ofInteger(String columnNames, Function<T, Integer> consumer) {
		return new Column<T, Integer>(columnNames, consumer);
	}
}