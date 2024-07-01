package org.unlaxer.util;

public class EpochPeriod implements Comparable<EpochPeriod> {

  long startInclusive;
  long endExclusive;

  public EpochPeriod(long startInclusive, long endExclusive) {
    super();
    this.startInclusive = startInclusive;
    this.endExclusive = endExclusive;
  }

  public EpochPeriod(long startInclusive) {
    super();
    this.startInclusive = startInclusive;
    this.endExclusive = startInclusive + 1;
  }


  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (endExclusive ^ (endExclusive >>> 32));
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
    if (endExclusive != other.endExclusive) {
      return false;
    }
    if (startInclusive != other.startInclusive) {
      return false;
    }
    return true;
  }

  @Override
  public int compareTo(EpochPeriod other) {

    long diff = startInclusive - other.startInclusive;
    if (diff == 0) {
      diff = endExclusive - other.endExclusive;
    }
    return diff > 0 ? 1 : diff < 0 ? -1 : 0;
  }

  @Override
  public String toString() {
    return startInclusive + "-" + endExclusive;
  }
}
