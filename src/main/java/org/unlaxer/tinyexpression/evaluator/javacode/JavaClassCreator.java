package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.BooleanVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NumberVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.StringVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.TypeHint;
import org.unlaxer.tinyexpression.parser.VariableParser;

public interface JavaClassCreator{
  default String createJavaClass(String className, TinyExpressionTokens tinyExpressionToken) {

    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();

    String calculationContextName = CalculationContext.class.getName();
    builder
      .setKind(Kind.Main)
      .line("import "+calculationContextName+";")
      .line("import org.unlaxer.tinyexpression.TokenBaseOperator;")
      .line("import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;")
      .n()
      .append("public class ")
      .append(className)
      .append(" implements TokenBaseOperator<"+calculationContextName+", Float>{")
      .n()
      .n()
      .setKind(Kind.Function)
      .incTab()
      .line("@Override")
      .line("public Float evaluate("+calculationContextName+" calculateContext , TinyExpressionTokens token) {")
      .setKind(Kind.Calculation)
      .incTab()
      .line("float answer = (float) ")
      .n();

    NumberExpressionBuilder.SINGLETON.build(builder, tinyExpressionToken.expressionToken, tinyExpressionToken);

    builder
      .setKind(Kind.Calculation)
      .n()
      .line(";")
      .line("return answer;")
      .decTab()
      .line("}")
      .decTab()
      .n();

    createMethods(builder, tinyExpressionToken , calculationContextName);
    
    builder
      .setKind(Kind.Main);


    String code = builder.toString();
    return code;
  }
  
  default void createMethods(SimpleJavaCodeBuilder builder , TinyExpressionTokens tinyExpressionTokens , String calculationContextName) {
    List<Token> methodTokens = tinyExpressionTokens.getMethodTokens();
    for (Token token : methodTokens) {
      
      Token typeHintToken = token.getChild(TokenPredicators.parserImplements(TypeHint.class));
      TypeHint typeHintParser = typeHintToken.getParser(TypeHint.class);
      String javaType = typeHintParser.type().javaType();
      
      Token methodNameToken = token.getChild(TokenPredicators.parsers(IdentifierParser.class));
      String methodName = methodNameToken.tokenString.get();
      
      List<Token> parameters = token.flatten().stream()
          .filter(TokenPredicators.parsers(
              StringVariableMethodParameterParser.class,
              NumberVariableMethodParameterParser.class,
              BooleanVariableMethodParameterParser.class))
          .collect(Collectors.toList());
      
      Token expression = token.getChild(TokenPredicators.parserImplements(ExpressionInterface.class));
      ExpressionInterface expressionParser = expression.getParser(ExpressionInterface.class);
      ExpressionType expressionType = expressionParser.expressionType();
      
      builder
        .n()
        .incTab()
        .append(javaType)
        .append(" ")
        .append(methodName)
        .append("("+calculationContextName+" calculateContext ");
      
      if(false == parameters.isEmpty()) {
        builder
          .append(",");
      }
      
      FormalParametersBuilder.buildParameter(builder, parameters);
      
      builder
        .append("){")
        .n()
        .incTab()
        .append(" return ");
        
//        型に沿ったbuilderでappend
      if(expressionType.isNumber()) {
        NumberExpressionBuilder.SINGLETON.build(builder, expression , tinyExpressionTokens);
      }else if(expressionType.isBoolean()) {
        BooleanExpressionBuilder.SINGLETON.build(builder, expression , tinyExpressionTokens);
      }else {
        builder.append(StringClauseBuilder.SINGLETON.build(expression , tinyExpressionTokens).toString());
      }
          
      builder
        .append(";")
        .n()
        .decTab()
        .line("}")
        .n()
        .n()
        .decTab();
    }
  }
  
  public static class FormalParametersBuilder  {
    

    public static void buildParameter(SimpleJavaCodeBuilder builder, List<Token> Parameters) {
      
      Iterator<Token> iterator = Parameters.iterator();
      
      while(iterator.hasNext()) {
        Token token = iterator.next();
        
        VariableParser parser = token.getParser(VariableParser.class);
        ExpressionType variableType = parser.typeAsOptional().get();
        String variableName = parser.getVariableName(token);
        builder
          .append(variableType.javaType())
          .append(" ")
          .append(variableName);
        
        if(iterator.hasNext()) {
          builder.append(" , ");
        }
      }
    }
  }

  
}