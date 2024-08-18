package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ResultTypeTest {

  @Test
  public void test() {
    System.out.println(Float.class.getCanonicalName());
    ResultType resultType = new ResultType("Float");
    assertEquals(Float.class, resultType.resulTypeClass);
  }

}
