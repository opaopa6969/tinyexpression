package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public class ResultTypeTest {

  @Test
  public void test() {
    System.out.println(Float.class.getCanonicalName());
    ExpressionType resultType = new ResultType("Float");
    assertEquals(Float.class, resultType.javaType());
  }

}
