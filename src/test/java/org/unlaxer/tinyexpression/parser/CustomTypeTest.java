package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.junit.Test;

public class CustomTypeTest {

  @Test
  public void test() {
    CustomType customType = new CustomType(ExpressionType.list,List.of(
        new CustomType(ExpressionType.tuple,
            List.of(
                new CustomType(ExpressionType.string),
                new CustomType(ExpressionType.number),
                new CustomType(ExpressionType.bool)
                )
            )
        )    
    );
    
    System.out.println(customType.toString());
  }
  
}
