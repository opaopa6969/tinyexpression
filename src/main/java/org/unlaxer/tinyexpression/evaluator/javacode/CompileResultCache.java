package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Map;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.NullSafeConcurrentHashMap;
import org.unlaxer.tinyexpression.TokenBaseOperator;

import net.openhft.compiler.CachedCompilerModifiedForByteCodeGetting.CompileResult;

public class CompileResultCache{
  
  static Map<String,CompileResult<TokenBaseOperator<CalculationContext, Float>>> 
    comipleResultByClassNameWothHash = new NullSafeConcurrentHashMap<>();
  
  public static CompileResult<TokenBaseOperator<CalculationContext, Float>> get(String classNameWithHash){
    return comipleResultByClassNameWothHash.get(classNameWithHash);
  }
  
  public static void set(String classNameWithHash ,CompileResult<TokenBaseOperator<CalculationContext, Float>> compileResult ){
    comipleResultByClassNameWothHash.put(classNameWithHash , compileResult);
  }
}