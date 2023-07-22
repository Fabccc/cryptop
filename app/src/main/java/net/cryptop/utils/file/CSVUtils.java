package net.cryptop.utils.file;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import net.cryptop.data.DataFrame;
import net.cryptop.utils.ChronoUtils;

public class CSVUtils {

  private CSVUtils() { throw new IllegalStateException("Utility class"); }

  public static final String COMMA_DELIMITER = ",";

  public static final String NEW_LINE_SEPARATOR = "\n";

  public static DataFrame loadCSV(String inputFile) {
    return ChronoUtils.time("Load file from " + inputFile, () -> {
      var dataFrame = new DataFrame();
      try {
        FileReader fileReader = new FileReader(inputFile);
        BufferedReader bufferedReader = new BufferedReader(fileReader);

        // first line is the header
        var header = bufferedReader.readLine().split(COMMA_DELIMITER);
        for (String field : header) {
          dataFrame.addField(field, new DoubleArrayList());
        }
        int fieldCount = header.length;

        // other lines are the data
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          var values = line.split(COMMA_DELIMITER);
          for (int i = 0; i < fieldCount; i++) {
            if (i >= values.length)
              break; // ignore empty values
            Double value =
                values[i].isEmpty() ? Double.NaN : Double.parseDouble(values[i]);
            dataFrame.addValue(header[i], value);
          }
        }

        bufferedReader.close();
        return dataFrame;
      } catch (Exception e) {
        System.out.println("Error in CsvFileReader !!!");
        e.printStackTrace();
        return dataFrame;
      }
    });
  }

  public static void writeCSV(DataFrame dataFrame, String outputFile) {
    ChronoUtils.time("Output file to " + outputFile, () -> {
      try {
        FileWriter fileWriter = new FileWriter(outputFile);
        for (String field : dataFrame.getFieldOrders()) {
          fileWriter.append(field);
          fileWriter.append(COMMA_DELIMITER);
        }
        fileWriter.append(NEW_LINE_SEPARATOR);
        for (int i = 0; i < dataFrame.size(); i++) {
          for (String field : dataFrame.getFieldOrders()) {
            Double d = dataFrame.get(field, i);
            if (Double.isNaN(d)) {
              fileWriter.append("");
            } else {
              fileWriter.append(String.valueOf(d));
            }
            fileWriter.append(COMMA_DELIMITER);
          }
          fileWriter.append(NEW_LINE_SEPARATOR);
        }
        fileWriter.flush();
        fileWriter.close();
      } catch (Exception e) {
        System.out.println("Error in CsvFileWriter !!!");
        e.printStackTrace();
      }
    });
  }
}