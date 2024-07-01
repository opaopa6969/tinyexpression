package org.unlaxer.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;



public class MultiDateParser {
  public enum Kind{
    full("yyyy-MM-dd HH:mm:ss"),
    fullWithUnderscore("yyyy-MM-dd_HH:mm:ss"),
    fullWithSlash("yyyy/MM/dd HH:mm:ss"),
    dateOnly("yyyy-MM-dd"),
    ISO_OFFSET_DATE_TIME(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    ;
    DateTimeFormatter dateTimeFormatter;

    private Kind(DateTimeFormatter dateTimeFormatter) {
      this.dateTimeFormatter = dateTimeFormatter;
    }
    
    private Kind(String format) {
      this.dateTimeFormatter = DateTimeFormatter.ofPattern(format);
    }
    
    public DateTimeFormatter formatter() {
      return dateTimeFormatter;
    }
    
    public Optional<String> format(long timestamp) {
      return format(timestamp,Optional.empty());
    }
    
    public Optional<String> format(long timestamp,Optional<Integer> offset) {
      Try<String> formated = Try.resultOf(() -> Instant.ofEpochMilli(timestamp)
          .atOffset(ZoneOffset.ofHours(offset.orElse(JPN_ZONE_OFFSET).intValue())).format(dateTimeFormatter));
      return formated.right();
    }
  }
  
  static final Integer JPN_ZONE_OFFSET = 9;
  
  public static Optional<Long> toEpochMilli(String date, Optional<Integer> timezone) {
    
    Try<Long> datetime = 
      Try.resultOf(()->applyTimezone(LocalDateTime.parse(date , Kind.full.formatter()), timezone))
        .fallback(()->applyTimezone(LocalDateTime.parse(date , Kind.fullWithUnderscore.formatter()), timezone))
        .fallback(()->applyTimezone(LocalDateTime.parse(date , Kind.fullWithSlash.formatter()), timezone))
        .fallback(()->applyTimezone(LocalDate.parse(date , Kind.dateOnly.formatter()).atStartOfDay(), timezone))
        .fallback(()->OffsetDateTime.parse(date , Kind.ISO_OFFSET_DATE_TIME.formatter()).toEpochSecond() * 1000)
        .fallback(()->Long.parseLong(date));
    return datetime.right();
  }
  
  public static void main(String[] args) {
    LocalDate parse = LocalDate.parse("1970-01-01" , Kind.dateOnly.formatter());
    LocalDateTime atStartOfDay = parse.atStartOfDay();
    System.out.println(atStartOfDay);
    Long millitime = toEpochMilli("1970-01-01", Optional.of(JPN_ZONE_OFFSET)).orElse(System.currentTimeMillis());
    System.out.println(Kind.fullWithSlash.format(millitime));
  }

  public static Long applyTimezone(LocalDateTime localDateTime, Optional<Integer> timezone) {
    Integer offset = timezone.orElse(JPN_ZONE_OFFSET);
    return localDateTime.toInstant(ZoneOffset.ofHours(offset)).toEpochMilli();
  }
  
}