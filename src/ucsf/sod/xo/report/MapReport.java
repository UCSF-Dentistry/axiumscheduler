package ucsf.sod.xo.report;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import ucsf.sod.util.SODExcelFactory;

public class MapReport<K, V> extends Report<Entry<K, V>>{
	
	public MapReport(String name, List<Column<Entry<K, V>, String>> columns) {
		super(name, columns);
	}

	/**
	 * Creates a report as a new sheet in the provided SoreniExcelFactory
	 * @param s the sheet by which to put the new report into
	 * @param iter the items to report on
	 */
	public void generate(SODExcelFactory parent, Iterator<Entry<K, V>> iter) {
		
		SODExcelFactory sheet = parent.createSheet(name);
		
		generateHeader(sheet);
		
		// Generate data
		while(iter.hasNext()) {
			sheet.createRow();
			Entry<K, V> t = iter.next();
			for(Column<Entry<K, V>, String> c : columns) {
				c.generateData(sheet, t);
			}
		}
	}
}