package org.unlaxer.tinyexpression.factory;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.ASTCreator;
import org.unlaxer.tinyexpression.evaluator.javacode.ExpressionBuilder;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder;
import org.unlaxer.tinyexpression.parser.FormulaParser;

import net.openhft.compiler.CachedCompilerModifiedForByteCodeGetting.CompileResult;
import net.openhft.compiler.CompilerUtils;
import net.openhft.compiler.CompilerUtilsModifedForGettingByteCode;

public class ContextCalculatorFactory{
  
  @SuppressWarnings("unchecked")
  public static ExtendedContextCalculator create(String formula , String className , String javaCode ,  byte[] byteCode) {
    
    Class<ContextCalculator> calculatorClass = null;
    
    try {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      
        try {
          calculatorClass = (Class<ContextCalculator>) classLoader.loadClass(className);
          
        } catch (ClassNotFoundException e) {
          
          calculatorClass = CompilerUtils.defineClass(classLoader , className, byteCode);
        } 
        ContextCalculator
          instance = (ContextCalculator) calculatorClass.getDeclaredConstructor().newInstance();
        
        return new SimpleContextCalculator(formula , javaCode , byteCode, instance);
        
    } catch (InstantiationException |IllegalAccessException | IllegalArgumentException |
        InvocationTargetException | NoSuchMethodException | SecurityException e) {

      throw new RuntimeException(e);
    }
  }
  
  public static ExtendedContextCalculator create(String formula , String className) {
    
    try(ParseContext parseContext = new ParseContext(new StringSource(formula));){
      Parsed parsed = getParser().parse(parseContext);
      if(false == parsed.isSucceeded()) {
        throw new IllegalArgumentException("failed to parse:"+formula);
      }
      Token rootToken = tokenReduer().apply(parsed.getRootToken(true));
      String javaCode = createJavaClass(className, rootToken);
      
      CalculatorAndByteCode calculator = compile(javaCode, className);
      byte[] byteCode = calculator.bytes;
      return new SimpleContextCalculator(formula, javaCode , byteCode ,  calculator.contextCalculator);
    }catch (Exception e) {
      throw new IllegalArgumentException("failed to parse:"+formula,e);
    }
  }
  
  static Parser getParser() {
    return Parser.get(FormulaParser.class);
  }
  
  
  static UnaryOperator<Token> tokenReduer(){
    return ASTCreator.SINGLETON;
  }
  
  static String createJavaClass(String className, Token rootToken) {
    
    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
    
    String CalculationContextName = CalculationContext.class.getName();
    
    String ContextCalculatorName = ContextCalculator.class.getName();
    builder
    .line("import org.unlaxer.Token;")
    .line("import "+CalculationContextName+";")
    .line("import "+ContextCalculatorName+";")
    .n()
    .append("public class ")
    .append(className)
    .append(" implements ContextCalculator{")
    .n()
    .n()
    .incTab()
    .line("@Override")
    .line("public Float apply(CalculationContext calculateContext){")
    .incTab()
    .line("float answer = (float) ")
    .n();
    
    ExpressionBuilder.SINGLETON.build(builder, rootToken);
    
    builder
    .n()
    .line(";")
    .line("return answer;")
    .decTab()
    .line("}");
    
    String code = builder.toString();
    return code;
  }
  
  @SuppressWarnings("unchecked")
  static CalculatorAndByteCode compile(String javaCode , String className){
    
    try {
      
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      if(loaded(classLoader , className)) {
        
        var calculatorClass = (Class<ContextCalculator>) classLoader.loadClass(className);
        ContextCalculator instance = (ContextCalculator) calculatorClass.getDeclaredConstructor().newInstance();
        
        synchronized (CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER) {
          CompileResult<Function<CalculationContext, Float>> loadFromJava =
              (CompileResult<Function<CalculationContext, Float>>) 
              CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(classLoader/*new ClassLoader() {}*/ , className, javaCode);
          
          byte[] byteCode = loadFromJava.byteCode;
          
          return new CalculatorAndByteCode(instance,byteCode);
        }
        
      }else {
        
        synchronized (CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER) {
          CompileResult<ContextCalculator> loadFromJava =
              (CompileResult<ContextCalculator>) 
              CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(classLoader , className, javaCode);
          ContextCalculator instance = (ContextCalculator) loadFromJava.loadedClass.getDeclaredConstructor().newInstance();
          byte[] byteCode = loadFromJava.byteCode;
          
          return new CalculatorAndByteCode(instance,byteCode);
        }
      }
    } catch (ClassNotFoundException | InstantiationException |IllegalAccessException | IllegalArgumentException |
        InvocationTargetException | NoSuchMethodException | SecurityException e) {
      
      throw new RuntimeException(e);
    }
  }
  
  static boolean loaded(ClassLoader classLoader , String className) {
    try {
      classLoader.loadClass(className);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
  
  static class CalculatorAndByteCode{
    
    public final ContextCalculator contextCalculator;
    public final byte[] bytes;
    public CalculatorAndByteCode(ContextCalculator contextCalculator, byte[] bytes) {
      super();
      this.contextCalculator = contextCalculator;
      this.bytes = bytes;
    }
  }
  
}