package net.cryptop.indicators;

import net.cryptop.data.DataClasses;
import net.cryptop.data.DataFrame;

public class RSIIndicator implements Indicator {

  private int period;

  public RSIIndicator(int period) { this.period = period; }

  @Override
  public String fieldName() {
    return "RSI" + period;
  }

  @Override
  public double apply(int index, DataFrame dataFrameSubSet) {
    // ignore index

    double[] values = dataFrameSubSet.getDoubles(DataClasses.CLOSE_FIELD);
    double[] gains = new double[period];
    double[] losses = new double[period];
    for (int i = 1; i < period; i++) {
      double diff = values[i] - values[i - 1];
      if (diff > 0) {
        gains[i] = diff;
      } else {
        losses[i] = -diff;
      }
    }
    double avgGain = 0;
    double avgLoss = 0;
    for (int i = 0; i < period; i++) {
      avgGain += gains[i];
      avgLoss += losses[i];
    }
    avgGain /= period;
    avgLoss /= period;
    double rs = avgGain / avgLoss;
    return 100 - (100 / (1 + rs));
  }

  @Override
  public int period() {
    return period;
  }

  @Override
  public String toString() {
    return "RSI" + period;
  }
}
