package org.unlaxer.tinyexpression.instances;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.unlaxer.tinyexpression.Calculator;

public interface TinyExpressionInstancesCache extends TinyExpressionInstances{
  
  boolean clearCache(TenantID tenantID);
  
  List<Calculator<?>> get(
      TenantID tenantID,
      Comparator<Calculator<?>> comparator , 
      Predicate<Calculator<?>> passFilter , 
      ClassLoader classLoader);
}