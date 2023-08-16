package ucsf.sod.xo.report;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ucsf.sod.util.SODExcelFactory;

public class Report<T> {
	
	protected final String name;
	protected final List<Column<T, String>> columns;
	
	public Report(String name, List<Column<T, String>> columns) {
		this.name = name;
		this.columns = new ArrayList<Column<T, String>>(columns);
	}

	protected void generateHeader(SODExcelFactory sheet) {
		sheet.createRow();
		for(Column<T, String> c : columns) {
			c.generateHeader(sheet);
		}
	}
	
	/**
	 * Creates a report as a new sheet in the provided SoreniExcelFactory
	 * @param s the sheet by which to put the new report into
	 * @param iter the items to report on
	 */
	public void generate(SODExcelFactory parent, Iterator<T> iter) {
		
		SODExcelFactory sheet = parent.createSheet(name);

		generateHeader(sheet);
		
		// Generate data
		while(iter.hasNext()) {
			sheet.createRow();
			T t = iter.next();
			for(Column<T, String> c : columns) {
				c.generateData(sheet, t);
			}
		}
	}
}