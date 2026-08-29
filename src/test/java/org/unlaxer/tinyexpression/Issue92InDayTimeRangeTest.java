package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;

import org.junit.Test;

/**
 * Regression for tinyexpression#92:
 * {@code inDayTimeRange} returned {@code false} for the same day when
 * {@code fromHour > toHour} (a midnight-spanning range), inconsistent with
 * {@link org.unlaxer.tinyexpression.function.EmbeddedFunction#inTimeRange}.
 */
public class Issue92InDayTimeRangeTest {

  private static CalculationContext ctx(int dayOfWeek, float hour) {
    CalculationContext ctx = CalculationContext.newConcurrentContext();
    ctx.set("nowDayOfWeek", (float) dayOfWeek);
    ctx.set("nowHour", hour);
    return ctx;
  }

  // --- Same-day midnight span: MONDAY 22 -> MONDAY 6 ---

  @Test
  public void sameDayMidnightSpan_lateHourIsIncluded() {
    CalculationContext ctx = ctx(DayOfWeek.MONDAY.getValue(), 23f);
    assertTrue(ctx.inDayTimeRange(DayOfWeek.MONDAY, 22f, DayOfWeek.MONDAY, 6f));
  }

  @Test
  public void sameDayMidnightSpan_earlyHourIsIncluded() {
    CalculationContext ctx = ctx(DayOfWeek.MONDAY.getValue(), 3f);
    assertTrue(ctx.inDayTimeRange(DayOfWeek.MONDAY, 22f, DayOfWeek.MONDAY, 6f));
  }

  @Test
  public void sameDayMidnightSpan_boundaryFromHourIsIncluded() {
    CalculationContext ctx = ctx(DayOfWeek.MONDAY.getValue(), 22f);
    assertTrue(ctx.inDayTimeRange(DayOfWeek.MONDAY, 22f, DayOfWeek.MONDAY, 6f));
  }

  @Test
  public void sameDayMidnightSpan_boundaryToHourIsExcluded() {
    CalculationContext ctx = ctx(DayOfWeek.MONDAY.getValue(), 6f);
    assertFalse(ctx.inDayTimeRange(DayOfWeek.MONDAY, 22f, DayOfWeek.MONDAY, 6f));
  }

  @Test
  public void sameDayMidnightSpan_gapHourIsExcluded() {
    CalculationContext ctx = ctx(DayOfWeek.MONDAY.getValue(), 12f);
    assertFalse(ctx.inDayTimeRange(DayOfWeek.MONDAY, 22f, DayOfWeek.MONDAY, 6f));
  }

  // --- Consistency with inTimeRange for the same hour pair ---

  @Test
  public void sameDayMidnightSpan_matchesInTimeRange() {
    CalculationContext ctx = ctx(DayOfWeek.MONDAY.getValue(), 23f);
    boolean fromDayRange = ctx.inDayTimeRange(DayOfWeek.MONDAY, 22f, DayOfWeek.MONDAY, 6f);
    boolean fromTimeRange = org.unlaxer.tinyexpression.function.EmbeddedFunction
        .inTimeRange(ctx, 22f, 6f);
    assertEquals(fromTimeRange, fromDayRange);
  }

  // --- Normal same-day (non-midnight) range still works ---

  @Test
  public void sameDayNormalRange_included() {
    CalculationContext ctx = ctx(DayOfWeek.MONDAY.getValue(), 12f);
    assertTrue(ctx.inDayTimeRange(DayOfWeek.MONDAY, 10f, DayOfWeek.MONDAY, 18f));
  }

  @Test
  public void sameDayNormalRange_excluded() {
    CalculationContext ctx = ctx(DayOfWeek.MONDAY.getValue(), 20f);
    assertFalse(ctx.inDayTimeRange(DayOfWeek.MONDAY, 10f, DayOfWeek.MONDAY, 18f));
  }
}
