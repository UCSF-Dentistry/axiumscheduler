package ucsf.sod.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.stream.Collector;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class SODUtil {
	private SODUtil() { }
	
	public static File getTimestampedFile(String path, String filename, String extension) {
		String timestamp = LocalDate.now().toString().replace("-", "");
		File out = new File(path + File.separator + filename + "-" + timestamp + "." + extension);
		int count = 0;
		while(out.exists()) {
			out = new File(path + File.separator + filename + "-" + timestamp +"-"+(++count)+ "." + extension);
		}
		return out;
	}

	public static Workbook openWorkbook(String file) throws IOException {
		return openWorkbook(new File(file));
	}
	
	@SuppressWarnings("resource")
	public static Workbook openWorkbook(File f) throws IOException {
		if(f.getAbsolutePath().endsWith(".xlsx")) {
			return new XSSFWorkbook(new FileInputStream(f));
		} else {
			return new HSSFWorkbook(new FileInputStream(f));
		}
	}

	public static final Collector<Double, double[], Double> VARIANCE_COLLECTOR = Collector.of( // See https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
        () -> new double[3], // {count, mean, M2}
        (acu, d) -> { // See chapter about Welford's online algorithm and https://math.stackexchange.com/questions/198336/how-to-calculate-standard-deviation-with-streaming-inputs
            acu[0]++; // Count
            double delta = d - acu[1];
            acu[1] += delta / acu[0]; // Mean
            acu[2] += delta * (d - acu[1]); // M2
        },
        (acuA, acuB) -> { // See chapter about "Parallel algorithm" : only called if stream is parallel ...
            double delta = acuB[1] - acuA[1];
            double count = acuA[0] + acuB[0];
            acuA[2] = acuA[2] + acuB[2] + delta * delta * acuA[0] * acuB[0] / count; // M2
            acuA[1] += delta * acuB[0] / count;  // Mean
            acuA[0] = count; // Count
            return acuA;
        },
        acu -> acu[2] / (acu[0] - 1.0), // Var = M2 / (count - 1)
        Collector.Characteristics.UNORDERED
	);
}
