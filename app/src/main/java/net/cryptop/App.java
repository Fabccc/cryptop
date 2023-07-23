/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package net.cryptop;

import java.text.DecimalFormat;
import java.util.logging.Logger;

import net.cryptop.config.Config;
import net.cryptop.data.DataClasses;
import net.cryptop.data.DataFrame;
import net.cryptop.indicators.Indicator;
import net.cryptop.utils.IOUtils;
import net.cryptop.utils.binance.BinanceData;
import net.cryptop.wallet.Wallet;

public class App {

  private static final DecimalFormat df = new DecimalFormat("#.##");
  private static final Logger logger;

  static {
    System.setProperty("java.util.logging.SimpleFormatter.format",
        "[%1$tF %1$tT] [%4$-7s] %5$s %n");
    logger = Logger.getLogger(App.class.getName());
  }

  public static Logger getLogger() {
    return logger;
  }

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
    if (config.getTimeSettings().startDate() == 0L) {
      System.out.println("Error in config.json: startDate is not set. "
          + "Please edit config.json and set startDate to a valid timestamp (in milliseconds).");
      System.exit(1);
    }

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
      long from = config.getTimeSettings().startDate();
      var historicalData = BinanceData
          .getHistoricalData(pair, from, config.getTimeSettings().interval())
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
      for (var indicator : config.getIndicators()) {
        logger.info("  - " + indicator);
      }
      // apply indicators
      Indicator.process(dataFrame, config.getIndicators());
      // save data to CSV
      dataFrame.saveToCSV("data/" + pair.symbol() + "_indicators.csv");
    }

    logger.info("Starting backtesting ...");

    for (var pair : pairs) {
      // load data
      var dataFrame = DataFrame.loadCSV("data/" + pair.symbol() + "_indicators.csv");
      // backtest
      logger.info("Testing strategies on " + pair + " (50 " +
          pair.stableCoin() + ")...");
      for (var strategy : config.getStrategies()) {
        logger.info("  - " + strategy.getName());
      }

      for (var strategy : config.getStrategies()) {
        dataFrame.freezeFinanceFields();
        var historicalData = dataFrame.toHistoricalData(pair);
        logger.info("Testing " + strategy.getName() + " on " + pair + " ...");
        // run strategy
        Wallet initialWallet = new Wallet();
        initialWallet.setBalance(pair.stableCoin(), 50.0);

        var result = strategy.run(initialWallet.clone(), historicalData, dataFrame);
        var finalWallet = result.wallet();
        var trades = result.trades();
        // compare initial and final wallet
        // var initialBalance = initialWallet.getBalance(pair.stableCoin());
        // var finalBalance = finalWallet.getBalance(pair.stableCoin());
        long firstDate = dataFrame.getLong(DataClasses.DATE_FIELD, 0);
        long lastDate = dataFrame.getLong(DataClasses.DATE_FIELD, dataFrame.size() - 1);
        long duration = lastDate - firstDate;
        logger.info("  - Duration: " + (duration / 1000 / 60 / 60 / 24) + " days, with interval " +
            config.getTimeSettings().interval().tag() + " (" + duration + " ms)");

        var initialWalletValue = initialWallet.getValue(firstDate, historicalData);
        var finalWalletValue = finalWallet.getValue(lastDate, historicalData);

        var profit = finalWalletValue - initialWalletValue;
        double profitPercent = profit / initialWalletValue * 100;
        var periodOfTimeInDays = duration / 1000 / 60 / 60 / 24;
        double profitPerYear = profitPercent / periodOfTimeInDays * 365;

        logger.info("  - Initial balance: " + initialWalletValue + " " +
            pair.stableCoin());
        logger.info("  - Final balance: " + finalWalletValue + " " +
            pair.stableCoin());
        String profitStr = profit > 0 ? "+" + profit : "" + profit;
        logger.info("  - Profit: " + profitStr + " " + pair.stableCoin());
        logger.info(
            "  - Profit (%): " + df.format(profitPercent) + "%, averaging " + df.format(profitPerYear) + "% per year");
        logger.info("  - Trades: " + trades.size());
        // save results
        logger.info("Saving results to CSV ...");
        String csvFileName = "results/" + pair.symbol() + "_" + strategy.getName() + ".csv";
        var resultsDataFrame = result.toDataFrame(historicalData);
        resultsDataFrame.saveToCSV(csvFileName);
      }
    }
  }
}
