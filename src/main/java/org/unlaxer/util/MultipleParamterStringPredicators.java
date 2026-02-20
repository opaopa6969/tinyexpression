package org.unlaxer.util;

import java.util.function.BiPredicate;

public class MultipleParamterStringPredicators {

  public static boolean match(BiPredicate<String, String> stringPredicator, String base, String... targetClause) {
    for (String target : targetClause) {
      if (stringPredicator.test(base, target)) {
        return true;
      }
    }
    return false;
  }

  public static boolean contains(String base, String... targetClause) {
    
    return match(
        (_base,_target)->_base.contains(_target) , 
        base , 
        targetClause
    );
  }
  
  public static boolean startsWith(String base, String... targetClause) {
    
    return match(
        (_base,_target)->_base.startsWith(_target) , 
        base , 
        targetClause
    );
  }
  
  public static boolean endsWith(String base, String... targetClause) {
    
    return match(
        (_base,_target)->_base.endsWith(_target) , 
        base , 
        targetClause
    );
  }
  
  public static boolean in(String base, String... targetClause) {
    
    return match(
        (_base,_target)->_base.equals(_target) , 
        base , 
        targetClause
    );
  }

}
