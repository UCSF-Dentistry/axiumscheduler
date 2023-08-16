package ucsf.sod.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class SODExcelFactory {
	
	private final Workbook workbook;
	private final Sheet currentSheet;
	private Row currentRow = null;
	private CellStyle currentCellStyle = null;
	
	public interface CellStyleFactory {
		CellStyle apply(CellStyle style);
	}
	
	public static enum Color {
		
		RED(HSSFColor.HSSFColorPredefined.RED),
		ORANGE(HSSFColor.HSSFColorPredefined.ORANGE),
		YELLOW(HSSFColor.HSSFColorPredefined.YELLOW),
		GREEN(HSSFColor.HSSFColorPredefined.GREEN),
		SEA_GREEN(HSSFColor.HSSFColorPredefined.SEA_GREEN),
		OLIVE_GREEN(HSSFColor.HSSFColorPredefined.OLIVE_GREEN),
		BLUE(HSSFColor.HSSFColorPredefined.BLUE),
		DARK_BLUE(HSSFColor.HSSFColorPredefined.DARK_BLUE),
		VIOLET(HSSFColor.HSSFColorPredefined.VIOLET),
		PINK(HSSFColor.HSSFColorPredefined.PINK)
		;
		
		public final HSSFColor.HSSFColorPredefined color;
		
		Color(HSSFColor.HSSFColorPredefined color) {
			this.color = color;
		}
		
		public Font applyColor(Font font) {
			font.setColor(color.getIndex());
			return font;
		}
	}
	
	public static enum TextColor {
		RED(Color.RED),
		ORANGE(Color.ORANGE),
		YELLOW(Color.YELLOW),
		GREEN(Color.GREEN),
		SEA_GREEN(Color.SEA_GREEN),
		OLIVE_GREEN(Color.OLIVE_GREEN),
		BLUE(Color.BLUE),
		DARK_BLUE(Color.DARK_BLUE),
		VIOLET(Color.VIOLET),
		PINK(Color.PINK)
		;
		
		public final Color color;
		TextColor(Color color) {
			this.color = color;
		}
		
		public CellStyle apply(CellStyle style, Font f) {
			style.setFont(color.applyColor(f));
			return style;
		}
	}
	
	public static enum BackgroundColor implements CellStyleFactory {
		RED(Color.RED),
		ORANGE(Color.ORANGE),
		YELLOW(Color.YELLOW),
		GREEN(Color.GREEN),
		SEA_GREEN(Color.SEA_GREEN),
		OLIVE_GREEN(Color.OLIVE_GREEN),
		BLUE(Color.BLUE),
		DARK_BLUE(Color.DARK_BLUE),
		VIOLET(Color.VIOLET),
		PINK(Color.PINK)
		;

		public final Color color;
		BackgroundColor(Color color) {
			this.color = color;
		}
		
		public CellStyle apply(CellStyle style) {
			return apply(style, FillPatternType.SOLID_FOREGROUND);
		}
		
		public CellStyle apply(CellStyle style, FillPatternType type) {
			style.setFillForegroundColor(color.color.getIndex());
			style.setFillPattern(type);
			return style;
		}
	}
	
	public static enum TextFormatting implements CellStyleFactory {
		WRAPPED {
			public CellStyle apply(CellStyle style) {
				style.setWrapText(true);
				return style;
			}
		},
		ROTATED {
			public CellStyle apply(CellStyle style) {
				style.setRotation((short)90);
				return style;
			}
		};
	}
	
	public SODExcelFactory() {
		this(new XSSFWorkbook(), null);
	}
	
	private SODExcelFactory(Workbook wb, Sheet s) {
		this.workbook = wb;
		if(s == null) {
			s = workbook.createSheet();
		}
		this.currentSheet = s;
		
		currentCellStyle = TextFormatting.WRAPPED.apply(workbook.createCellStyle());
	}
	
	public SODExcelFactory createSheet() {
		return new SODExcelFactory(workbook, workbook.createSheet());
	}
	
	public SODExcelFactory createSheet(String sheetname) {
		return new SODExcelFactory(workbook, workbook.createSheet(sheetname));
	}
	
	/**
	 * Gets the sheet from this workbook, if it exists
	 * @param sheetname the name of the sheet to get
	 * @return a SoreniExcelFactory that has the sheet active; otherwise, null
	 */
	public SODExcelFactory getSheet(String sheetname) {
		Sheet s = workbook.getSheet(sheetname);
		if(s == null) {
			return null;
		}
		return new SODExcelFactory(workbook, s);
	}
	
	public void setActiveCellStyle(CellStyle style) {
		if(style instanceof HSSFCellStyle) {
			if(!(workbook instanceof HSSFWorkbook)) {
				throw new RuntimeException("Cell style ["+style.getClass()+"] is not compatible with workbook ["+workbook.getClass()+"]");
			}
		} else if(style instanceof XSSFCellStyle) {
			if(!(workbook instanceof XSSFWorkbook)) {
				throw new RuntimeException("Cell style ["+style.getClass()+"] is not compatible with workbook ["+workbook.getClass()+"]");
			}
		} else {
			throw new RuntimeException("Unrecognized cell style class: " + style.getClass());
		}
		currentCellStyle = style;
	}
	
	public void setTextColor(TextColor color) {
		setActiveCellStyle(color.apply(workbook.createCellStyle(), workbook.createFont()));
	}

	public SODExcelFactory createRow() {
		if(currentSheet == null) {
			createSheet();
		}
		
		currentRow = currentSheet.createRow(currentSheet.getPhysicalNumberOfRows());
		return this;
	}
	
	public SODExcelFactory createRowAndSkip(int cellsToSkip) {
		createRow();
		for(int i = cellsToSkip; i > 0; i--) {
			createCell("");
		}
		
		return this;
	}
	
	private static Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
	public SODExcelFactory createRow(Collection<String> l) {
		createRow();
		for(String s : l) {
			if(INTEGER_PATTERN.matcher(s).matches()) {
				createCell(Integer.parseInt(s));
			} else {
				createCell(s);
			}
		}
		return this;
	}
	
	public SODExcelFactory createRow(String... s) {
		createRow();
		for(String _s : s) {
			if(INTEGER_PATTERN.matcher(_s).matches()) {
				createCell(Integer.parseInt(_s));
			} else {
				createCell(_s);
			}
		}
		
		return this;
	}

	public SODExcelFactory createRow(Collection<String> l, CellStyleFactory factory) {
		var currentStyle = currentCellStyle;
		setActiveCellStyle(factory.apply(workbook.createCellStyle()));
		createRow(l);
		currentCellStyle = currentStyle;
		return this;
	}

	private Cell _createCell() {
		if(currentRow == null) {
			createRow();
		}
		
		Cell c = currentRow.createCell(currentRow.getPhysicalNumberOfCells());
		c.setCellStyle(currentCellStyle);
		return c;
	}
	
	public SODExcelFactory blankCell() {
		return createCell("", null);		
	}
	
	public SODExcelFactory blankCell(int count) {
		while(count-- > 0)  {
			createCell("", null);
		}
		
		return this;
	}
	
	public SODExcelFactory fillCell(int value, int count) {
		while(count-- > 0) {
			createCell(value);
		}
		
		return this;
	}

	public SODExcelFactory fillCell(String value, int count) {
		while(count-- > 0) {
			createCell(value);
		}
		
		return this;
	}

	public SODExcelFactory fillCellAndMerge(String value, int count) {
		createCell(value);
		blankCell(count-1);
		var asdf = getCurrentPosition().getRight();
		mergeCellsCurrentRow(asdf-count, asdf-1);
		
		return this;
	}
	
	public SODExcelFactory createCells(String... s) {
		for(String _s : s) {
			createCell(_s);
		}
		return this;
	}
	
	public SODExcelFactory createCells(Collection<String> c) {
		for(String s : c) {
			createCell(s);
		}
		
		return this;
	}

	public SODExcelFactory createCell(char value) {
		return createCell(Character.toString(value), null);
	}

	public SODExcelFactory createCell(long value) {
		return createCell(value, null);
	}

	public SODExcelFactory createCell(String value) {
		if(INTEGER_PATTERN.matcher(value).matches()) {
			return createCell(Integer.parseInt(value));
		} else {
			return createCell(value, null);
		}
	}
	
	public SODExcelFactory createCell(Object o) {
		return createCell(o, null);
	}
	
	public SODExcelFactory createCell(long value, String comment) {
		Cell c = _createCell();
		c.setCellValue(value);
		setComment(c, comment);
		return this;
	}
	
	public SODExcelFactory createCell(String value, String comment) {
		Cell c = _createCell();
		c.setCellValue(value);
		setComment(c, comment);
		return this;
	}
	
	public SODExcelFactory createCell(Object o, String comment) {
		Cell c = _createCell();
		c.setCellValue(o.toString());
		setComment(c, comment);
		return this;
	}
	
	private void setComment(Cell c, String comment) {
		if(comment == null || comment.length() == 0)
			return;
		
		CreationHelper helper = workbook.getCreationHelper();

		ClientAnchor anchor = helper.createClientAnchor();
		anchor.setCol1(c.getColumnIndex());
		anchor.setCol2(c.getColumnIndex()+5);
		anchor.setRow1(c.getRow().getRowNum());
		anchor.setRow2(c.getRow().getRowNum()+comment.split("\\r?\\n").length);
		
		Comment _comment = currentSheet.createDrawingPatriarch().createCellComment(anchor);
		_comment.setString(helper.createRichTextString(comment));
		_comment.setAuthor("Apache POI");
		c.setCellComment(_comment);
	
	}
	
	/**
	 * 
	 * @return return the row (left), column (right)
	 */
	public Pair<Integer, Integer> getCurrentPosition() {
		return Pair.of(currentRow.getRowNum(), (int)(currentRow.getLastCellNum()));
	}
	
	public int getCurrentRowNumber() {
		return currentRow.getRowNum();
	}
	
	public void mergeCellsCurrentRow(int firstColumn, int lastColumn) {
		currentSheet.addMergedRegion(new CellRangeAddress(currentRow.getRowNum(), currentRow.getRowNum(), firstColumn, lastColumn));
	}
	
	public void mergeCells(int firstRow, int lastRow, int firstColumn, int lastColumn) {
		currentSheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn));
	}
	
	public void autofitAllColumns() {
		autofitColumns(0, currentRow.getLastCellNum() - 1);
	}
	
	public void autofitColumn(int colnum) {
		currentSheet.autoSizeColumn(colnum);
	}
	
	public void autofitColumns(int startCol, int endCol) {
		for(int i = startCol; i <= endCol; i++) {
			currentSheet.autoSizeColumn(i);
		}
	}
	
	public void setColumnWidth(int colnum, int width) {
		currentSheet.setColumnWidth(colnum, toExcelWidth(width));
	}
	
	public void setSheetToFront() {
		workbook.setSheetOrder(currentSheet.getSheetName(), 0);
		workbook.setActiveSheet(0);
	}
	
	public void export(String fileName) throws IOException {
		export(new File(fileName));
	}
	
	public void export(File f) throws IOException {
		try {
			workbook.write(new FileOutputStream(f));
		} catch(FileNotFoundException ex) {
			throw ex;
		}
	}
	
	@Override
	public int hashCode() {
		return workbook.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		else if(obj == null || !(obj instanceof SODExcelFactory))
			return false;
		
		SODExcelFactory o = (SODExcelFactory)obj;
		return workbook == o.workbook;
	}
	
	/**
	 * Utility function to convert excel width to POI width
	 * @param excel width
	 * @return POI width
	 */
	public static int toExcelWidth(int width) {
		return (width << 8) + 200;
	}
}
