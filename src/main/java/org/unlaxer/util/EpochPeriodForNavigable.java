package org.unlaxer.util;

/**
 * this class is subclass of EpochPeriod with compare with start epoch for
 * NavigableMap<EpochStartWithEnd,T>
 *
 */
public class EpochPeriodForNavigable implements Comparable<EpochPeriodForNavigable> {

  public long startInclusive;
  public long endExclusive;
  public boolean isInstant;

  public EpochPeriodForNavigable() {
    super();
  }

  public EpochPeriodForNavigable(String hyphenedStartInclusiveAndEndExclusive) {

    String[] split = hyphenedStartInclusiveAndEndExclusive.split("-");
    startInclusive = Long.parseLong(split[0]);
    endExclusive = Long.parseLong(split[1]);
    isInstant = startInclusive + 1 == endExclusive;
  }

  public EpochPeriodForNavigable(long startInclusive, long endExclusive) {
    super();
    this.startInclusive = startInclusive;
    this.endExclusive = endExclusive;
    isInstant = false;
  }

  public EpochPeriodForNavigable(long startInclusive) {
    super();
    this.startInclusive = startInclusive;
    this.endExclusive = startInclusive + 1;
    isInstant = true;
  }

  @Override
  public int hashCode() {
    //
    final int prime = 31;
    int result = 1;
    // result = prime * result + (int) (endExclusive ^ (endExclusive >>> 32));
    result = prime * result + (int) (startInclusive ^ (startInclusive >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    EpochPeriod other = (EpochPeriod) obj;
    // if (endExclusive != other.endExclusive) {
    // return false;
    // }
    return contains(other.startInclusive);
  }

  @Override
  public int compareTo(EpochPeriodForNavigable other) {

    if (isInstant) {
      boolean contains = other.contains(startInclusive);
      if (contains) {
        return 0;
      }
    }

    long diff = startInclusive - other.startInclusive;
    // if (diff == 0) {
    // diff = endExclusive - other.endExclusive;
    // }
    return diff > 0 ? 1 : diff < 0 ? -1 : 0;
  }

  @Override
  public String toString() {
    return startInclusive + "-" + endExclusive;
  }

  public boolean contains(long epoch) {
    return epoch >= startInclusive && epoch < endExclusive;
  }
  
  public boolean equalsStartAndEnd(EpochPeriodForNavigable other) {
    return startInclusive == other.startInclusive && endExclusive == other.endExclusive;
  }

}
