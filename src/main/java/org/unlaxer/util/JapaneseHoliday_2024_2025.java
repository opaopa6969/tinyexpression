package org.unlaxer.util;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.unlaxer.tinyexpression.CalculationContext;

public class JapaneseHoliday_2024_2025 {
  
  static String[] holiDayStrings= {
      "2024-01-01",
      "2024-01-08",
      "2024-02-11",
      "2024-02-12",
      "2024-02-23",
      "2024-03-20",
      "2024-04-29",
      "2024-05-03",
      "2024-05-04",
      "2024-05-05",
      "2024-05-06",
      "2024-07-15",
      "2024-08-11",
      "2024-08-12",
      "2024-09-16",
      "2024-09-22",
      "2024-09-23",
      "2024-10-14",
      "2024-11-03",
      "2024-11-04",
      "2024-11-23",
      "2025-01-01",
      "2025-01-13",
      "2025-02-11",
      "2025-02-23",
      "2025-02-24",
      "2025-03-20",
      "2025-04-29",
      "2025-05-03",
      "2025-05-04",
      "2025-05-05",
      "2025-05-06",
      "2025-07-21",
      "2025-08-11",
      "2025-09-15",
      "2025-09-23",
      "2025-10-13",
      "2025-11-03",
      "2025-11-23",
      "2025-11-24"
  };
  
  
  static Set<LocalDate> holidays = new HashSet<>();
  
  static {
    for(String day:holiDayStrings) {
      LocalDate localDate = LocalDate.parse(day);
      holidays.add(localDate);
    }
  }
  
  
  public static boolean isHoliDay(CalculationContext calculationContext) {
    
    LocalDate nowLoalDate = calculationContext.getObject(LocalDate.class)
        .orElseGet(LocalDate::now);
    
    return holidays.contains(nowLoalDate);
  }

}
