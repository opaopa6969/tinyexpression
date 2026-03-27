package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.BooleanVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.IfNotExistsParser;
import org.unlaxer.tinyexpression.parser.NumberVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.ObjectSetterParser;
import org.unlaxer.tinyexpression.parser.ObjectVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.StringVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.TypeHint;
import org.unlaxer.tinyexpression.parser.VariableParser;

public interface GeneralJavaClassCreator{
  
  default String createJavaClass(String className, TinyExpressionTokens tinyExpressionToken, 
      SpecifiedExpressionTypes specifiedExpressionTypes) {
    
    TypedToken<ExpressionInterface> expressionToken = tinyExpressionToken.expressionToken;
    ExpressionInterface parser = expressionToken.getParser();
    
    //TODO determine which use resultType or parser.expressionType()
//    String returningType = parser.expressionType().javaType().getSimpleName();
    ExpressionType resultType = specifiedExpressionTypes.resultType();
    String returningType = resultType.javaTypeAsString();
    
    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();

    String calculationContextName = CalculationContext.class.getName();
    builder
      .setKind(Kind.Main)
      .line("import "+calculationContextName+";")
//      .line("import org.unlaxer.tinyexpression.TokenBaseOperator;")
//      .line("import org.unlaxer.tinyexpression.factory.ContextCalculator;")
      .line("import org.unlaxer.Token;")
      .n()
      .append("public class ")
      .append(className)
      .append(" implements org.unlaxer.tinyexpression.TokenBaseCalculator{")
//      .append(returningType)
//      .append(">{")
    
//    .append(" implements TokenBaseOperator<"+calculationContextName+", Float>{")
//      .append(" implements ContextCalculator {")
      
      .n()
      .n()
      .setKind(Kind.Function)
      .incTab()
      .line("@Override")
      .append("public ")
      .append(returningType)
      .line(" evaluate("+calculationContextName+" calculateContext , Token token) {")
//      .line("public Float apply(org.unlaxer.tinyexpression.CalculationContext calculateContext){")
      .setKind(Kind.Calculation)
      .incTab()
      .append(returningType)
      .append(" answer = (")
      .append(returningType)
      .line(") ")
      .n();
    
    if(resultType.isNumber()) {
      
      NumberExpressionBuilder.SINGLETON.build(builder, expressionToken, tinyExpressionToken);

    }else if(resultType.isString()) {
      
      StringExpressionBuilder.SINGLETON.build(builder, expressionToken, tinyExpressionToken);
  
    }else if(resultType.isBoolean()) {
      
      BooleanExpressionBuilder.SINGLETON.build(builder, expressionToken, tinyExpressionToken);

    } else if (resultType.isObject()) {
      ExpressionType expressionType = parser.expressionType();
      Token normalizedNumberExpression = NumberExpressionBuilder.SINGLETON.unwrapNumberExpressionToken(expressionToken);
      if (normalizedNumberExpression.parser instanceof NakedVariableParser) {
        TypedToken<VariableParser> typedVariable = normalizedNumberExpression.typed(VariableParser.class);
        String variableName = typedVariable.getParser().getVariableName(typedVariable);
        ExpressionType resolvedType = VariableTypeResolver
            .resolveFromVariableParserToken(normalizedNumberExpression, tinyExpressionToken)
            .orElse(ExpressionTypes.object);
        if (resolvedType.isObject()) {
          appendObjectVariableAccess(builder, tinyExpressionToken, variableName);
        } else {
          NumberExpressionBuilder.SINGLETON.build(builder, expressionToken, tinyExpressionToken);
        }
      } else if (expressionType.isNumber()) {
        NumberExpressionBuilder.SINGLETON.build(builder, expressionToken, tinyExpressionToken);
      } else if (expressionType.isBoolean()) {
        BooleanExpressionBuilder.SINGLETON.build(builder, expressionToken, tinyExpressionToken);
      } else {
        ExpressionOrLiteral build = StringClauseBuilder.SINGLETON.build(expressionToken, tinyExpressionToken);
        build.populateTo(builder, Kind.Function);
        builder.append(build.toString());
      }
    }

    builder
      .setKind(Kind.Calculation)
      .n()
      .line(";")
      .line("return answer;")
      .decTab()
      .line("}")
      .decTab()
      .n();

    createMethods(builder, tinyExpressionToken , calculationContextName , resultType);
    
    builder
      .setKind(Kind.Main);

    String code = builder.toString();
//    System.out.println(code);
    return code;
  }

  default void appendObjectVariableAccess(SimpleJavaCodeBuilder builder, TinyExpressionTokens tinyExpressionToken,
      String variableName) {
    Optional<Token> declaration = tinyExpressionToken.matchedVariableDeclaration(variableName);
    if (declaration.isPresent()) {
      Optional<Token> setter = declaration.get().getChildWithParserAsOptional(ObjectSetterParser.class);
      if (setter.isPresent()) {
        Token setterToken = setter.get();
        Token expression = setterToken.getChild(TokenPredicators.parserImplements(ExpressionInterface.class));
        String expressionCode = objectExpressionCode(expression, tinyExpressionToken);
        Optional<Token> ifNotExists = setterToken.getChildWithParserAsOptional(IfNotExistsParser.class);
        if (ifNotExists.isPresent()) {
          builder.append("calculateContext.getObject(")
              .w(variableName)
              .append(",java.lang.Object.class).orElse(")
              .append(expressionCode)
              .append(")");
        } else {
          builder.append("calculateContext.setAndGetObject(")
              .w(variableName)
              .append(",")
              .append(expressionCode)
              .append(",java.lang.Object.class)");
        }
        return;
      }
    }
    builder.append("calculateContext.getObject(")
        .w(variableName)
        .append(",java.lang.Object.class).orElse(null)");
  }

  default String objectExpressionCode(Token expression, TinyExpressionTokens tinyExpressionToken) {
    return objectExpressionCode(expression, tinyExpressionToken, List.of());
  }

  default String objectExpressionCode(Token expression, TinyExpressionTokens tinyExpressionToken,
      List<String> localParameterNames) {
    if (expression.getParser() instanceof VariableParser) {
      TypedToken<VariableParser> typedVariable = expression.typed(VariableParser.class);
      String variableName = typedVariable.getParser().getVariableName(typedVariable);
      if (localParameterNames.contains(variableName)) {
        return variableName;
      }
      ExpressionType resolvedType = VariableTypeResolver
          .resolveFromVariableParserToken(expression, tinyExpressionToken)
          .orElse(ExpressionTypes.object);
      if (resolvedType.isObject()) {
        SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
        appendObjectVariableAccess(builder, tinyExpressionToken, variableName);
        return builder.getBuilder(Kind.Main).toString();
      }
    }

    ExpressionType expressionType = expression.getParser(ExpressionInterface.class).expressionType();
    if (expressionType.isObject() && !expression.filteredChildren.isEmpty()) {
      Token nestedExpression = expression.filteredChildren.get(0);
      if (nestedExpression.getParser() instanceof ExpressionInterface) {
        return objectExpressionCode(nestedExpression, tinyExpressionToken, localParameterNames);
      }
    }
    SimpleJavaCodeBuilder expressionBuilder = new SimpleJavaCodeBuilder();
    if (expressionType.isNumber()) {
      NumberExpressionBuilder.SINGLETON.build(expressionBuilder, expression, tinyExpressionToken);
      return expressionBuilder.getBuilder(Kind.Main).toString();
    }
    if (expressionType.isBoolean()) {
      BooleanExpressionBuilder.SINGLETON.build(expressionBuilder, expression, tinyExpressionToken);
      return expressionBuilder.getBuilder(Kind.Main).toString();
    }
    ExpressionOrLiteral built = StringClauseBuilder.SINGLETON.build(expression, tinyExpressionToken);
    built.populateTo(expressionBuilder, Kind.Function);
    return built.toString();
  }
  
  default void createMethods(SimpleJavaCodeBuilder builder , TinyExpressionTokens tinyExpressionTokens , 
      String calculationContextName , ExpressionType resultType) {
    List<Token> methodTokens = tinyExpressionTokens.getMethodTokens();
    for (Token token : methodTokens) {
      
      Token typeHintToken = token.getChild(TokenPredicators.parserImplements(TypeHint.class));
      TypeHint typeHintParser = typeHintToken.getParser(TypeHint.class);
      String javaType = typeHintParser.type().javaType().getSimpleName();
      
      Token methodNameToken = token.getChild(TokenPredicators.parsers(IdentifierParser.class));
      String methodName = methodNameToken.tokenString.get();
      
      List<Token> parameters = token.flatten().stream()
          .filter(TokenPredicators.parsers(
              StringVariableMethodParameterParser.class,
              NumberVariableMethodParameterParser.class,
              BooleanVariableMethodParameterParser.class,
              ObjectVariableMethodParameterParser.class))
          .collect(Collectors.toList());
      List<String> parameterNames = parameters.stream()
          .map(parameter -> {
            TypedToken<VariableParser> typed = parameter.typed(VariableParser.class);
            return typed.getParser().getVariableName(typed);
          })
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
      } else if (expressionType.isObject()) {
        builder.append(objectExpressionCode(expression, tinyExpressionTokens, parameterNames));
      }else {
        ExpressionOrLiteral build = StringClauseBuilder.SINGLETON.build(expression , 
            tinyExpressionTokens);
        build.populateTo(builder, Kind.Function);
        builder.append(build.toString());
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
        
        TypedToken<VariableParser> typed = token.typed(VariableParser.class);
        VariableParser parser = typed.getParser();
        ExpressionType variableType = parser.typeAsOptional().get();
        String variableName = parser.getVariableName(typed);
        builder
          .append(variableType.javaType().getSimpleName())
          .append(" ")
          .append(variableName);
        
        if(iterator.hasNext()) {
          builder.append(" , ");
        }
      }
    }
  }

  
}
