/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package net.cryptop;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.logging.Logger;
import net.cryptop.config.Config;
import net.cryptop.data.DataFrame;
import net.cryptop.indicators.Indicator;
import net.cryptop.indicators.NoiseGatherer;
import net.cryptop.indicators.SMAIndicator;
import net.cryptop.indicators.SavgolFilterIndicator;
import net.cryptop.utils.IOUtils;
import net.cryptop.utils.binance.BinanceData;
import net.cryptop.utils.binance.BinanceData.IntervalEnum;

public class App {

  private static final Logger logger;

  static {
    System.setProperty("java.util.logging.SimpleFormatter.format",
                       "[%1$tF %1$tT] [%4$-7s] %5$s %n");
    logger = Logger.getLogger(App.class.getName());
  }

  public static Logger getLogger() { return logger; }

  public static void main(String[] args) {
    System.out.println("===== Starting App =====");
    // read config from config.json

    Config config = Config.loadConfig().orElseGet(() -> {
      // if config.json does not exist, create it
      System.out.println("Creating default config.json ...");
      Config defaultConfig = Config.defaultConfig();
      System.out.print("Enter your Binance API key: ");
      String apiKey = IOUtils.readStdIn();
      System.out.print("Enter your Binance secret key: ");
      String secretKey = IOUtils.readStdIn();
      defaultConfig.setMainCredentials(
          new Config.BinanceCredentials(apiKey, secretKey));

      defaultConfig.saveConfig();
      return defaultConfig;
    });

    logger.info("Config loaded: " + config);

    var pairs = config.getPairs();
    logger.info("Loaded " + pairs.size() + " pairs:");
    pairs.forEach(pair -> logger.info("  - " + pair));

    // download data from Binance
    for (var pair : pairs) {
      boolean hasBeenDownloaded = BinanceData.hasBeenDownloaded(pair);
      if (hasBeenDownloaded) {
        // ask user if they want to download again
        System.out.print("Data for " + pair + " has already been downloaded. "
                         + "Do you want to download again? (y/n) ");
        String answer = IOUtils.readStdIn();
        if (!answer.equals("y")) {
          continue;
        }
      }
      logger.info("Downloading data for " + pair + " ...");
      long nowMinusOneYear =
          LocalDateTime.now().minusYears(1).toEpochSecond(ZoneOffset.UTC) *
          1000;
      var historicalData =
          BinanceData
              .getHistoricalData(pair, nowMinusOneYear, IntervalEnum.DAYS_1)
              .join();
      logger.info("Downloaded " + historicalData.size() + " candles.");

      logger.info("Saving data to CSV ...");
      // save data to CSV
      var dataFrame = historicalData.toDataFrame();
      dataFrame.saveToCSV("data/" + pair.symbol() + ".csv");
      logger.info("Saved data to CSV.");
    }

    logger.info("Applying indicators ...");
    for (var pair : pairs) {
      // load inital data
      var dataFrame = DataFrame.loadCSV("data/" + pair.symbol() + ".csv");
      logger.info("Applying indicators to " + pair + " ...");
      // apply indicators
      Indicator.process(dataFrame,
                        List.of(new SMAIndicator(50), new SMAIndicator(200),
                                new SavgolFilterIndicator(50, 3),
                                new NoiseGatherer()));
      // save data to CSV
      dataFrame.saveToCSV("data/" + pair.symbol() + "_indicators.csv");
    }
  }
}