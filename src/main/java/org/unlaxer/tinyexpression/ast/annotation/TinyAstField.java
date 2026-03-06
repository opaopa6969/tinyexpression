package org.unlaxer.tinyexpression.ast.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(TinyAstFields.class)
public @interface TinyAstField {
  String name();

  int childIndex() default -1;

  TinyAstFieldSource source() default TinyAstFieldSource.CHILD;
}
