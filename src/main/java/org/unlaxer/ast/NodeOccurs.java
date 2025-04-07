package org.unlaxer.ast;

public enum NodeOccurs{
  Zero(0,0),
  One(1,1),
  Two(2,2),
  Three(3,3),
  Four(4,4),
  Optional(0,1),
  ZeroOrOne(0,1),
  ZeroOrMore(0,Integer.MAX_VALUE),
  OneOrMore(1,Integer.MAX_VALUE),
  ;
  public final int min;
  public final int max;
  private NodeOccurs(int min, int max) {
    this.min = min;
    this.max = max;
  }
}