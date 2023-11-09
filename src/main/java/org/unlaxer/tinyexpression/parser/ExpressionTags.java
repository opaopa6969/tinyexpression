package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Tag;

public enum ExpressionTags{
  returning,
  condition,
  thenClause,
  elseClause,
  matchExpression,
  matchCase,
  matchCaseFactor,
  matchDefaultFactor,
  thenAndElse,
  ;
  Tag tag;

  private ExpressionTags() {
    this.tag = new Tag(this);
  }
  
  public Tag tag() {
    return tag;
  }
}