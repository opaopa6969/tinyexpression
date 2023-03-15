package org.unlaxer.tinyexpression.function;

import java.util.Optional;

import org.unlaxer.tinyexpression.CalculationContext;

public class EmbeddedFunction {

  /**
   * Assumes valid values are provided for hours.
   *
   * Range of possible values that should be used [0-23]
   *
   * @param fromHour The hour at which the time range starts
   * @param toHour   The hour at which the time range ends (n.b not inclusive -
   *                 given a 'toHour' of 4, a 'now' of 4 is NOT in range)
   * @return isInTimeRange
   */
  public static boolean inTimeRange(CalculationContext calculationContext, float fromHour, float toHour) {
    Optional<Float> nowHourOpt = calculationContext.getValue("nowHour");
    if (nowHourOpt.isEmpty()) {
      return false;
    }

    float nowHour = nowHourOpt.get();
    boolean spansMidnight = fromHour > toHour; // e.g fromHour = 22, toHour = 7, means spansMidnight will be true

    if (spansMidnight) {
      return (nowHour >= fromHour) || (nowHour < toHour);
    } else {
      return (nowHour >= fromHour) && (nowHour < toHour);
    }
  }

  public static float toNum(String input, float defaultReturnValue) {
    try {
      return Float.parseFloat(input);
    } catch (NumberFormatException nfe) {
      return defaultReturnValue;
    }
  }

}
