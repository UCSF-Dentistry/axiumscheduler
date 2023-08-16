package ucsf.sod.util;

import java.time.LocalDate;
import java.util.Optional;

import org.apache.poi.hssf.usermodel.HSSFPalette;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

public class ExcelUtil {

	public static interface ExcelCellReader {
		
		public default FormulaEvaluator getFormulaEvaluator(Row r) {
			return r.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
		}
		public int getIndex();
	
		public default CellType getCellType(Row r) {
			return r.getCell(getIndex()).getCellType();
		}
		
		public default String getCellAddress(Row r) {
			return r.getCell(getIndex()).getAddress().formatAsString();
		}
		
		public default LocalDate getLocalDateFromRow(Row r) {
			Cell c = r.getCell(getIndex());
			if(!DateUtil.isCellDateFormatted(c)) {
				if(c.getCellType() == CellType.STRING) {
					return LocalDate.parse(c.getStringCellValue());
				}
				throw new RuntimeException("Cell "+c.getAddress().formatAsString()+" is not formatted as a date");
			} else if(c.getCellType() == CellType.NUMERIC) {
				return c.getLocalDateTimeCellValue().toLocalDate();
			} else if(c.getCellType() == CellType.FORMULA) {
				return LocalDate.parse(new DataFormatter().formatCellValue(c, getFormulaEvaluator(r)));
			} else {
				throw new RuntimeException("Cell type is not NUMERIC or FORMULA: " + c.getCellType());
			}
		}
		
		public default Optional<CellRangeAddress> getMergedRegion(Row r) {
		    for(CellRangeAddress mergedCell : r.getSheet().getMergedRegions()) { // TODO: cache this call somehow
		        if(mergedCell.isInRange(r.getRowNum(), getIndex())) {
		        	return Optional.of(mergedCell);
		        }
		    }
		    
		    return Optional.empty();
		}
		
		public default String getValueFromRow(Row r) {
			
			// First check if the cell is in a merged region
			Cell c = null;			
			{
				Optional<CellRangeAddress> result = getMergedRegion(r);
				if(result.isPresent()) {
					CellRangeAddress mergedCell = result.get();
		        	c = r.getSheet().getRow(mergedCell.getFirstRow()).getCell(mergedCell.getFirstColumn());					
				}
			}
			
		    // If it isn't in a merged region, it is likely blank
			if(c == null) {
		    	c = r.getCell(getIndex(), MissingCellPolicy.CREATE_NULL_AS_BLANK);
			}
			
			return switch(c.getCellType()) {
				case STRING:
					yield c.getStringCellValue().trim();
				case NUMERIC:
					if(DateUtil.isCellDateFormatted(c)) {
						yield c.getLocalDateTimeCellValue().toLocalDate().toString(); 
					} else {
						yield Double.toString(c.getNumericCellValue());
					}
				case BLANK:
					yield "";
				case FORMULA:
					FormulaEvaluator eval = getFormulaEvaluator(r);
					CellValue value = eval.evaluate(c);
					yield switch(value.getCellType()) {
						case STRING:
							yield value.getStringValue();
						case NUMERIC:
							if(DateUtil.isCellDateFormatted(c)) {
								yield new DataFormatter().formatCellValue(c, eval);
							} else {
								yield Double.toString(value.getNumberValue());
							}
						default:
							throw new RuntimeException("Unexpected formula cell type: " + value);
					};
				default:
					throw new RuntimeException("Unexpeced cell type: " + c.getCellType());
			};
		} 
	}
	
	public static Cell createCell(Row r, int colNum, String data) {
		Cell c = r.createCell(colNum);
		c.setCellValue(data);
		return c;
	}

	public static Cell createCell(Row r, int colNum, String data, String comment) {
		Cell c = r.createCell(colNum);
		c.setCellValue(data);
		
		CreationHelper helper = r.getSheet().getWorkbook().getCreationHelper();
	
		int commentHeight = 3;
		String[] lineCount = comment.split("\n");
		if(lineCount.length > commentHeight) {
			commentHeight = lineCount.length;
		}
		
		ClientAnchor anchor = helper.createClientAnchor();
		anchor.setCol1(c.getColumnIndex());
		anchor.setCol2(c.getColumnIndex()+5);
		anchor.setRow1(c.getRow().getRowNum());
		anchor.setRow2(c.getRow().getRowNum()+commentHeight);
		
		Comment _comment = r.getSheet().createDrawingPatriarch().createCellComment(anchor);
		_comment.setString(helper.createRichTextString(comment));
		_comment.setAuthor("Apache POI");
		c.setCellComment(_comment);
		
		return c;
	}

	public static HSSFColor getForegroundColor(Cell c) {
		return getPalette(c.getSheet().getWorkbook()).getColor(c.getCellStyle().getFillForegroundColor());
	}

	public static HSSFPalette getPalette(Workbook wb) {
		return ((HSSFWorkbook)wb).getCustomPalette();
	}
}
