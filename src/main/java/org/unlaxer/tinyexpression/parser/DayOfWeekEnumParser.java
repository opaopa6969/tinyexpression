package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.SuggestableParser;

import java.time.DayOfWeek;

public class DayOfWeekEnumParser extends SuggestableParser {
  private static String[] DAYS_OF_WEEK = new String[DayOfWeek.values().length];

  static {
    for (int i = 0; i < DayOfWeek.values().length; i++) {
      DAYS_OF_WEEK[i] = DayOfWeek.of(i + 1).name();
    }
  }

  public DayOfWeekEnumParser() {
    super(false, DAYS_OF_WEEK);
  }

  @Override
  public String getSuggestString(String matchedString) {
    return matchedString;
  }
}