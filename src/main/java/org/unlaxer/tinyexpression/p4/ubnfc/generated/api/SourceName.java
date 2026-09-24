package org.unlaxer.tinyexpression.p4.ubnfc.generated.api;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/** Original IR name when a generated Java identifier has to be escaped. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.RECORD_COMPONENT})
public @interface SourceName { String value(); }
