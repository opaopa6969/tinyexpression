package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.DivisionParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionType.PrePost;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.MethodInvocationParser;
import org.unlaxer.tinyexpression.parser.MinusParser;
import org.unlaxer.tinyexpression.parser.MultipleParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.PlusParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.ToNumParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.function.CosParser;
import org.unlaxer.tinyexpression.parser.function.MaxParser;
import org.unlaxer.tinyexpression.parser.function.MinParser;
import org.unlaxer.tinyexpression.parser.function.RandomParser;
import org.unlaxer.tinyexpression.parser.function.SinParser;
import org.unlaxer.tinyexpression.parser.function.SquareRootParser;
import org.unlaxer.tinyexpression.parser.function.TanParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberCaseExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberFactorParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberIfExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberMatchExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberSetterParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberTermParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringLengthParser;

public class NumberExpressionBuilder implements TokenCodeBuilder {

  public static class NumberCaseExpressionBuilder implements TokenCodeBuilder{

    public static NumberCaseExpressionBuilder SINGLETON = new NumberCaseExpressionBuilder();

    public void build(SimpleJavaCodeBuilder builder, Token token ,
        TinyExpressionTokens tinyExpressionTokens) {

      List<Token> originalTokens = token.filteredChildren;
      Iterator<Token> iterator = originalTokens.iterator();

      while(iterator.hasNext()){
        Token caseFactor = iterator.next();

        Token booleanExpression = caseFactor.filteredChildren.get(0);
        Token expression = caseFactor.filteredChildren.get(1);
        BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression ,
            tinyExpressionTokens);
        builder.append(" ? ");
        NumberExpressionBuilder.SINGLETON.build(builder, expression , 
            tinyExpressionTokens);
        builder
          .append(":")
          .n();
      }
    }
  }
  
  public static NumberExpressionBuilder SINGLETON = new NumberExpressionBuilder();

  public void build(SimpleJavaCodeBuilder builder, Token token , 
		  TinyExpressionTokens tinyExpressionTokens) {
    
    ExpressionType numberType = tinyExpressionTokens.numberType();
    PrePost wrapNumber = numberType.wrapNumber();

    Parser parser = token.parser;
    
    if(parser instanceof NumberExpressionParser) {
      
      token = token.filteredChildren.get(0);
      parser = token.parser;
      
      if (parser instanceof NumberTermParser) {
        
        token = token.filteredChildren.get(0);
        parser = token.parser;
        
        if(parser instanceof NumberFactorParser) {
          token = token.filteredChildren.get(0);
          parser = token.parser;
          
        }
      }
    }
    
    if (parser instanceof PlusParser) {

      binaryOperate(builder, token, "+" , tinyExpressionTokens);

    } else if (parser instanceof MinusParser) {

      binaryOperate(builder, token, "-" , tinyExpressionTokens);

    } else if (parser instanceof MultipleParser) {

      binaryOperate(builder, token, "*" , tinyExpressionTokens);

    } else if (parser instanceof DivisionParser) {

      binaryOperate(builder, token, "/" , tinyExpressionTokens);

    } else if (parser instanceof NumberParser) {
      
      String numberWithSuffix = numberType.numberWithSuffix(token.tokenString.get());

      builder.append(numberWithSuffix);

    } else if (parser instanceof NakedVariableParser || parser instanceof NumberVariableParser) {

      Optional<ExpressionType> fromVariableParserToken = 
          VariableTypeResolver.resolveFromVariableParserToken(token, tinyExpressionTokens);

      TypedToken<VariableParser> typed = token.typed(VariableParser.class);
      
      VariableBuilder.build(this, builder, typed, tinyExpressionTokens, NumberSetterParser.class,
          numberType.zeroNumber(),"getValue","setAndGet",fromVariableParserToken.orElse(ExpressionTypes.number));
//      List<Token> variableDeclarationsTokens = tinyExpressionTokens.getVariableDeclarationTokens();
//      
//      
////      上のリストが入っているのでこれを利用してsetをする
//      
//      String variableName = 
//          parser instanceof NakedVariableParser ? 
//            NakedVariableParser.getVariableName(token):
//            NumberVariableParser.getVariableName(token);
//      
//      boolean isMatch =false;
//      for (Token declarationTtoken : variableDeclarationsTokens) {
//        Token nakedVariableToken = declarationTtoken.getChildWithParser(NakedVariableParser.class);
//        String _variableName = NakedVariableParser.getVariableName(nakedVariableToken);
//        
//        if(_variableName.equals(variableName)) {
//          Optional<Token> numberSetterToken = declarationTtoken.getChildWithParserAsOptional(NumberSetterParser.class);
//          if(numberSetterToken.isEmpty()) {
//            continue;
//          }
//          Token _numberSetterToken = numberSetterToken.get();
//          Token expression = _numberSetterToken.getChild(TokenPredicators.parserImplements(ExpressionInterface.class));
//          Optional<Token> ifNotExists = _numberSetterToken.getChildWithParserAsOptional(IfNotExistsParser.class);
//          
//          SimpleJavaCodeBuilder simpleJavaCodeBuilder = new SimpleJavaCodeBuilder();
//          build(simpleJavaCodeBuilder, expression, tinyExpressionTokens);
//          String expseeionString = simpleJavaCodeBuilder.builder.toString();
////          String expseeionString = expression.getToken().orElseThrow();
//          
//          if(ifNotExists.isPresent()) {
//            
//            builder.append("calculateContext.getValue(").w(variableName).append(").orElse("+expseeionString+")");
//          }else {
//            builder.append("calculateContext.setAndGet(").w(variableName).append(","+expseeionString+")");
//          }
//          isMatch = true;
//          break;
//        }
//      }
//      if(false == isMatch) {
//        builder.append("calculateContext.getValue(").w(variableName).append(").orElse(0f)");
//      }

    } else if (parser instanceof NumberIfExpressionParser) {

      Token booleanExpression = IfExpressionParser.getBooleanExpression(token);
      Token factor1 = IfExpressionParser.getThenExpression(token , NumberExpression.class , booleanExpression);
      Token factor2 = IfExpressionParser.getElseExpression(token , NumberExpression.class , booleanExpression);

      /*
       * BooleanExpressionOperator.SINGLETON.evaluate(calculateContext, booleanExpression)?
       * factor1: factor2
       */

      builder.append("(");

      BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression , 
          tinyExpressionTokens);

      builder.append(" ? ").n().incTab();
      build(builder, factor1 , tinyExpressionTokens);

      builder.append(":").n();
      build(builder, factor2 , tinyExpressionTokens);

      builder.decTab();

      builder.append(")");

    } else if (parser instanceof NumberMatchExpressionParser) {

      Token caseExpression = token.getChild(TokenPredicators.parsers(NumberCaseExpressionParser.class));
      Token defaultCaseFactor = token.getChildFromAstNodes(1);

      builder.n();
      builder.incTab();

      builder.append("(");

      NumberCaseExpressionBuilder.SINGLETON.build(builder, caseExpression , 
          tinyExpressionTokens);
      builder.n();
      build(builder, defaultCaseFactor , tinyExpressionTokens);

      builder.append(")");
      builder.decTab();

    } else if (parser instanceof SinParser) {
      
      Token value = token.filteredChildren.get(0);
      builder.append(wrapNumber.pre());
      builder.append(" Math.sin(calculateContext.radianAngle(");
      build(builder, value , tinyExpressionTokens);
      builder.append(wrapNumber.post());
      builder.append("))");

    } else if (parser instanceof CosParser) {

      Token value = token.filteredChildren.get(0);
      builder.append(wrapNumber.pre());
      builder.append(" Math.cos(calculateContext.radianAngle(");
      build(builder, value , tinyExpressionTokens);
      builder.append(wrapNumber.post());
      builder.append("))");

    } else if (parser instanceof TanParser) {
      
      Token value = token.filteredChildren.get(0);
      builder.append(wrapNumber.pre());
      builder.append(" Math.tan(calculateContext.radianAngle(");
      build(builder, value , tinyExpressionTokens);
      builder.append(wrapNumber.post());
      builder.append("))");

    } else if (parser instanceof SquareRootParser) {

      Token value = token.filteredChildren.get(0);
      builder.append(wrapNumber.pre());
      builder.append(" Math.sqrt(");
      build(builder, value , tinyExpressionTokens);
      builder.append(")");
      builder.append(wrapNumber.post());

    } else if (parser instanceof MinParser) {
      
      
      builder.append(wrapNumber.pre());
      builder.append(" Math.min(");
      build(builder, token.filteredChildren.get(0) , tinyExpressionTokens);
      builder.append(",");
      build(builder, token.filteredChildren.get(1) , tinyExpressionTokens);
      builder.append(")");
      builder.append(wrapNumber.post());

    } else if (parser instanceof MaxParser) {

      builder.append(wrapNumber.pre());
      builder.append(" Math.max(");
      build(builder, token.filteredChildren.get(0) , tinyExpressionTokens);
      builder.append(",");
      build(builder, token.filteredChildren.get(1) , tinyExpressionTokens);
      builder.append(")");
      builder.append(wrapNumber.post());

    } else if (parser instanceof RandomParser) {

      builder.append(wrapNumber.pre());
      builder.append("calculateContext.nextRandom()");
      builder.append(wrapNumber.post());

    } else if (parser instanceof ToNumParser) {

      //TODO apply result type
      Token leftString = token.filteredChildren.get(0);
      Token rightFloatDefault = token.filteredChildren.get(1);

      builder.append("org.unlaxer.tinyexpression.function.EmbeddedFunction.toNum(");
      builder.append(StringClauseBuilder.SINGLETON.build(leftString , tinyExpressionTokens).toString());
      builder.append(",");
      build(builder, rightFloatDefault , tinyExpressionTokens);
      builder.append("f)");

    } else if (parser instanceof StringLengthParser) {

      Token stringExpressionToken = token.filteredChildren.get(0);//3rd children is inner
      String string = StringClauseBuilder.SINGLETON.build(stringExpressionToken , tinyExpressionTokens).toString();
      if(string == null || string.isEmpty()) {
        string ="\"\"";
      }
      builder
        .append(string)
        .append(".length()");

    }else if (parser instanceof SideEffectExpressionParser) {
      
      SideEffectExpressionBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);
      
//    } else if (parser instanceof StringIndexOfParser) {
//
//      return StringIndexOfOperator.SINGLETON.evaluate(calculateContext, token);
    }else if (parser instanceof MethodInvocationParser) {
      
      MethodInvocationBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
    }else {
      throw new IllegalArgumentException();
    }
  }

  void binaryOperate(SimpleJavaCodeBuilder builder, Token token, String operator ,
      TinyExpressionTokens tinyExpressionTokens) {

    builder.append("(");

    build(builder, token.filteredChildren.get(1) , tinyExpressionTokens);
    builder.append(operator);
    build(builder, token.filteredChildren.get(2) , tinyExpressionTokens);

    builder.append(")");
  }
}