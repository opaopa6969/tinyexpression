package org.unlaxer.parser.combinator;

public interface BasicCombinator{

  public boolean acceptsMulti();
  public boolean acceptsZero();
  public boolean acceptsOne();

  public boolean isQquantifier();
  public default boolean isStructure() {
    return false == isQquantifier();
  }
  public boolean isChain();
  public boolean isChoice();

}