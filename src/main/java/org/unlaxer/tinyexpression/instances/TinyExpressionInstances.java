package org.unlaxer.tinyexpression.instances;

import java.util.Comparator;
import java.util.List;

import org.unlaxer.tinyexpression.Calculator;

public interface TinyExpressionInstances {

  boolean clearCache(TenantID tenantID);

  List<Calculator> cache(
      TenantID tenantID,
      Comparator<Calculator> comparator ,
      ClassLoader classLoader);
}