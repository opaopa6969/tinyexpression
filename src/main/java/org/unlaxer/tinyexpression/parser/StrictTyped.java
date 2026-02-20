package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Tag;

public class StrictTyped extends Tag{
  
  public static final Tag tag = new StrictTyped();
  
  public static Tag get() {
    return tag;
  }

  public StrictTyped() {
    super("strictTyped");
  }
  
}