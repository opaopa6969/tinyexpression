package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.function.Function;
import java.util.function.Supplier;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser.MethodAndParameters;

public class SideEffectExpressionBuilder implements TokenCodeBuilder {

  
  public static SideEffectExpressionBuilder SINGLETON = new SideEffectExpressionBuilder();

  @Override
  public void build(SimpleJavaCodeBuilder builder, Token token , 
      TinyExpressionTokens tinyExpressionTokens) {
    
    MethodAndParameters methodAndParameters = 
        SideEffectExpressionParser.extract(token , tinyExpressionTokens);
    
    String methodName = methodAndParameters.classNameAndIdentifier.getIdentifier();
    String className = 
        tinyExpressionTokens.resolveJavaClass(
            methodAndParameters.classNameAndIdentifier.getClassName()
        );
    
        
    builder
      //    java.util.Optional<WhiteListSetter> function1 = calculateContext.getObject(
      //      org.unlaxer.tinyexpression.evaluator.javacode.WhiteListSetter.class);
      .setKind(Kind.Function)
      .append("java.util.Optional<")
      .append(className)
      .append("> ")
      .appendCurrentFunctionName()
      .append(" = calculateContext.getObject(")
      .n()
      .incTab()
      .append(className)
      .append(".class);")
      .decTab()
      .n()
      // function1.map(_function->_function.setWhiteList(calculateContext, 1.0f)).orElse(1.0f)  :
      .setKind(Kind.Calculation)
      .appendCurrentFunctionName()
      .append(".map(_function->_function.")
      .append(methodName)
      .append("(calculateContext , ");
    
    ParametersBuilder.buildParameter(builder, methodAndParameters , tinyExpressionTokens);
    
    builder
      .append(")).orElseThrow(()->new org.unlaxer.tinyexpression.Calculator.CalculationException(\"class not found in CalculationContext. please set :"+className+"\"))");
    // 1st implementation : first parameter is default returning value
    // 2nd implementation : "returning as type default xxx". xxx is default returning value
    //　　　　　　　　　　　　　　　　 : if returning clause is not exists , then first parameter is default returning value
    // 3rd implementation : orElseThrow! comment out returning default
    
//    Token returningToken = methodAndParameters.returningToken;
//    Parser parser = returningToken.parser;
////    TokenPrinter.output(returningToken, System.out);
//    if(parser instanceof NumberExpression) {
//      NumberExpressionBuilder.SINGLETON.build(builder, returningToken , tinyExpressionTokens);
//    }else if(parser instanceof StringExpression){
//      StringExpressionBuilder.SINGLETON.build(builder, returningToken , tinyExpressionTokens);
//    }else {
//      BooleanExpressionBuilder.SINGLETON.build(builder, returningToken , tinyExpressionTokens);
//    }
//    
//    builder
//      .append(")");
//      ;
    
  }

  static Supplier<Float> sampleSupplier = ()->{System.out.println("");return 10.0f;};
  static Function<Float,Float> sampleFunction= (x)->{x++;return x;};
  
  
}