# TinyExpression

-----

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.unlaxer/tinyExpression)

Tiny Expression is UDF(user defined function) for your application.

* function compiled to JavaCode.
* types are number , boolean , String and any javaTypes
* you can define and call java class


# usage

## simple

```java

package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.Name;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class SimpleUDFTest {

  @Test
  public void testSimple() {
    CalculationContext context = CalculationContext.newConcurrentContext();
    context.set("sex", "male");

    // create UDF
    String udf = "if($sex=='male'){500}else{1000}";

    // create calculator
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        Name.of("Test"), // name for identifier
        udf, // user define function
        new SpecifiedExpressionTypes(
            ExpressionTypes._float, // result type of this udf returning
            ExpressionTypes._float // default number type. eg. float,double,integer,short...
        ),
        Thread.currentThread().getContextClassLoader());// classloader for generated class from udf
    
    
    {
      // test with male
      float apply = (float)calculator.apply(context);
      assertEquals(500.0f, apply , 0.1);
    }

    
    {
      // test with female
      context.set("sex", "female");
      float apply = (float)calculator.apply(context);
      assertEquals(1000.0f, apply , 0.1);
    }
  }
}

```

## TinyExpressionEexcutor

this sample for managed the udf codes with multitenancy.


```java
```


```java
```


```java
```





# samples

```java
```


# BNF

<img src="https://opaopa6969.github.io/TinyExpression144.png"/>


```bnf
TinyExpression = Codes Imports VariableDeclarations Annotations Expressions Methods;
Codes = { Code };
Code = CodeStart CharactersWithoutTripleBacktick CodeEnd;
CodeStart = HeadLine '```' CodeScheme { ':' CodeIdentifier } '\n';
CodeEnd = HeadLine '```' '\n';
CodeScheme = Identifier;
CodeIdentifier = JavaClassName;
CharactersWithoutTripleBacktick = (? all visible characters excepts '```' ?);
HeadLine = (? pointer of head line. previous charator is null or one or more line feeds ?);
Imports = { Import };
Import = 'import' ( JavaClassMethod | JavaClassName ) 'as' ( Identifier { Identifier } ) ';';
VariableDeclarations = { VariableDeclaration };
VariableDeclaration = NumberVariableDeclaration | StringVariableDeclaration | BooleanVariableDeclaration | NakedVariableDeclaration;
NumberVariableDeclaration = ( 'variable' | 'var' ) NakedVariable ( NumberTypeDeclaration [ NumberSetter ] | NumberSetter ) Description ';';
NumberTypeDeclaration = [ 'as' ] NumberTypeHint;
StringVariableDeclaration = ( 'variable' | 'var' ) NakedVariable ( StringTypeDeclaration [ StringSetter ] | StringSetter ) Description ';';
StringTypeDeclaration = [ 'as' ] StringTypeHint;
BooleanVariableDeclaration = ( 'variable' | 'var' ) NakedVariable ( BooleanTypeDeclaration [ BooleanSetter ] | BooleanSetter ) Description ';';
BooleanTypeDeclaration = [ 'as' ] BooleanTypeHint;
NakedVariableDeclaration = ( 'variable' | 'var' ) NakedVariable Description ';';
StringSetter = 'set' [ 'if' 'not' 'exist' ] StringExpression;
BooleanSetter = 'set' [ 'if' 'not' 'exist' ] BooleanExpression;
NumberSetter = 'set' [ 'if' 'not' 'exist' ] NumberExpression;
Annotations = { LineAnnotation | Annotation };
LineAnnotation = '@' ( AlphabetNumericUnderScore { AlphabetNumericUnderScore } ) { (? all visible characters excepts '\n' ?) } '\n';
Annotation = '@' Identifier AnnotationParameters;
AnnotationParameters = '(' [ AnnotationParameter { ',' AnnotationParameter } ] ')';
AnnotationParameter = Identifier '=' ( StringExpression | BooleanExpression | NumberExpression );
Expressions = [ NumberExpression | StringExpression | BooleanExpression ];
Methods = { NumberMethod | StringMethod | BooleanMethod };
NumberExpression = NumberTerm { ( '+' | '-' ) NuberTerm };
NumberFactor = NumberSideEffectExpression | NumberMethodInvocation | NumberIfExpression | NumberMatchExpression | Number | NumberVariable | NakedVariable | '(' NumberExpression ')' | GetExpressionVariable | Sin | Cos | Tan | SquareRoot | Min | Max | Random | FactorOfString;
NumberTerm = NumberFactor { ( '*' | '/' ) NumberFactor };
SideEffectExpressionHeader = [ 'call' ] ( 'with' 'side' 'effect' | 'external' );
NumberSideEffectExpression = SideEffectExpressionHeader [ [ 'returning' ] NumberTypeHintSuffix ] [ ':' ] ( JavaClassMethod | Identifier ) '(' Arguments ')';
BooleanSideEffectExpression = SideEffectExpressionHeader ( [ 'returning' ] BooleanTypeHintSuffix ) [ ':' ] ( JavaClassMethod | Identifier ) '(' Arguments ')';
StringSideEffectExpression = SideEffectExpressionHeader ( [ 'returning' ] StringTypeHintSuffix ) [ ':' ] ( JavaClassMethod | Identifier ) '(' Arguments ')';
ArgumentChoice = StringExpression | BooleanExpression | NumberExpression;
Arguments = [ ArgumentChoice { ',' ArgumentChoice } ];
MethodInvocationHeader = 'call' 'internal' | 'call' | 'internal';
NumberMethodInvocation = [ MethodInvocationHeader ] Identifier (? match for retuning number method ?) '(' Arguments ')';
StringMethodInvocation = [ MethodInvocationHeader ] Identifier (? match for retuning string method ?) '(' Arguments ')';
BooleanMethodInvocation = [ MethodInvocationHeader ] Identifier (? match for retuning boolean method ?) '(' Arguments ')';
NumberIfExpression = 'if' '(' BooleanExpression ')' '{' NumberExpression '}' 'else' '{' NumberExpression '}';
StringIfExpression = 'if' '(' BooleanExpression ')' '{' StringExpression '}' 'else' '{' StringExpression '}';
BooleanIfExpression = 'if' '(' BooleanExpression ')' '{' BooleanExpression '}' 'else' '{' BooleanExpression '}';
NumberMatchExpression = 'match' '{' NumberCaseExpression NumberDefaultCaseFactor '}';
NumberCaseFactor = BooleanExpression '->' NumberExpression;
NumberCaseExpression = { NumberCaseFactor ',' };
NumberDefaultCaseFactor = 'default' '->' NumberExpression;
BooleanMatchExpression = 'match' '{' BooleanCaseExpression BooleanDefaultCaseFactor '}';
BooleanCaseFactor = BooleanExpression '->' BooleanExpression;
BooleanCaseExpression = { BooleanCaseFactor ',' };
BooleanDefaultCaseFactor = 'default' '->' BooleanExpression;
StringMatchExpression = 'match' '{' StringCaseExpression StringDefaultCaseFactor '}';
StringCaseFactor = BooleanExpression '->' StringExpression;
StringCaseExpression = { CaseBooleanFactor ',' };
StringDefaultCaseFactor = 'default' '->' StringExpression;
IsPresentBoolean = 'isPresent' '(' NakedVariable ')';
BooleanExpression = BooleanExpression { ( '==' | '!=' | '&' | '�b' | '^' ) BooleanExpression };
BooleanFactor = BooleanSideEffectExpression | BooleanMethodInvocation | 'true' | 'false' | 'not' '(' BooleanExpression ')' | '(' BooleanExpression ')' | IsPresentBoolean | NumberExpression '==' NumberExpression | NumberExpression '!=' NumberExpression | NumberExpression '>=' NumberExpression | NumberExpression '<=' NumberExpression | NumberExpression '>' NumberExpression | NumberExpression '<' NumberExpression | BooleanVariable | NakedVariable | GetBooleanVariable | BooleanExpressionOfString;
Number = [ '-' ] ( Digits '.' Digits | Digits '.' | Digits | '.' Digits ) [ Exponent ];
Exponent = ( 'e' | 'E' ) [ '-' ] ( Digit { Digit } );
Digit = '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9';
Digits = Digit { Digit };
Sin = 'sin' '(' NumberExpression ')';
Cos = 'cos' '(' NumberExpression ')';
Tan = 'tan' '(' NumberExpression ')';
SquareRoot = 'sqrt' '(' Expression ')';
Min = 'min' '(' NumberExpression ',' NumberExpression ')';
Max = 'max' '(' NumberExpression ',' NumberExpression ')';
Random = 'random' '(' ')';
StringLiteral = '"' { CharactersWithoutDoubleQuote } '"' | ''' { CharactersWithoutSingleQuote } ''';
StringFactor = StringSideEffectExpression | StringMethodInvocation | StringLiteral | StringVariable | Variable | GetStringVariable '(' StringExpression ')' | 'trim' '(' StringExpression ')' | 'toUpperCase' '(' StringExpression ')' | 'toLowerCase' '(' StringExpression ')';
StringExpression = StringTerm { '+' StringTerm };
StringTerm = StringFactor [ Slice ];
Slice = '[' [ NumberExpression ] ':' [ NumberExpression ] [ ':' NumberExpression ] ']';
BooleanExpressionOfString = StringExpression '==' StringExpression | StringExpression '!=' StringExpression | StringExpression '.' 'in' '(' StringExpression { ',' StringExpression } ')' | StringExpression '.' 'startsWith' '(' StringExpression ')' | StringExpression '.' 'endsWith' '(' StringExpression ')' | StringExpression '.' 'contains' '(' StringExpression ')';
FactorOfString = StringLength;
StringLength = 'len' '(' StringExpression ')';
StringTypeHint = 'String' | 'string';
StringTypeHintSuffix = [ 'as' ] StringTypeHint;
StringTypeHintPrefix = '(' StringTypeHint ')';
NumberTypeHint = 'Number' | 'number' | 'Float' | 'float';
NumberTypeHintSuffix = [ 'as' ] NumberTypeHint;
NumberTypeHintPrefix = '(' NumberTypeHint ')';
BooleanTypeHint = 'Boolean' | 'boolean';
BooleanTypeHintSuffix = [ 'as' ] BooleanTypeHint;
BooleanTypeHintPrefix = '(' BooleanTypeHint ')';
TypeHintSuffix = StringTypeHintSuffix | NumberTypeHintSuffix | BooleanTypeHintSuffix;
TypeHintPrefix = StringTypeHintPrefix | NumberTypeHintPrefix | BooleanTypeHintPrefix;
NakedVariable = '$' Identifier;
ExclusiveNakedVariable = '$' Identifier (? NO MATCH for TypeHintSuffix ?);
StringVariable = StringTypeHintPrefix NakedVariable | NakedVariablie StringTypeHintSuffix | (? NakedVariable matched in VariableDeclaration of string ?);
StringVariableMethodParamer = StringTypeHintPrefix NakedVariable | NakedVariablie StringTypeHintSuffix;
BooleanVariable = BooleanTypeHintPrefix NakedVariable | NakedVariablie BooleanTypeHintSuffix | (? NakedVariable matched in VariableDeclaration of boolean ?);
BooleanVariableMethodParameter = BooleanTypeHintPrefix NakedVariable | NakedVariablie BooleanTypeHintSuffix;
NumberVariable = NumberTypeHintPrefix Variable | Variablie NumberTypeHintSuffix | (? NakedVariable matched in VariableDeclaration of number ?);
NumberVariableMethodParameter = NumberTypeHintPrefix Variable | Variablie NumberTypeHintSuffix;
GetExpressionVariable = 'get' [ NumberTypeHint ] '(' NumberVariable ')' [ '.' 'orElse' '(' NumberExpression ')' ];
GetBooleanVariable = 'get' [ BooleanTypeHint ] '(' BooleanVariable ')' [ '.' 'orElse' '(' BooleanExpression ')' ];
GetStringVariable = 'get' [ StringTypeHint ] '(' StringVariable ')' [ '.' 'orElse' '(' StringExpression ')' ];
BlockComment = '/*' { [ LineAnnotation | (? all visible characters excepts '*/' ?) { (? all visible characters excepts '*/' ?) } ] } '*/';
CPPComment = '//' (? all visible characters excepts '//' ?) '\n';
AlphabetNumericUnderScoreSpace = (? alphabet and numeric and underscore and space ?);
AlphabetNumericUnderScore = (? alphabet and numeric and underscore ?);
AlphabetUnderScore = (? alphabet and and underscore ?);
CharactersWithoutDoubleQuote = (? all visible characters excepts '"' ?);
CharactersWithoutSingleQuote = (? all visible characters excepts "'" ?);
Identifier = AlphabetUnderScore { AlphabetNumericUnderScore };
Description = 'description' '=' StringLiteral;
JavaClassMethod = JavaClassAndHash Identifier;
JavaClassAndHash = JavaClassName '#';
JavaClassName = Identifier { '.' Identifier };
NumberMethod = NumberTypeHint Identifier MethodParameters '{' NumberExpression '}';
StringMethod = StringTypeHint Identifier MethodParameters '{' StringExpression '}';
BooleanMethod = BooleanTypeHint Identifier MethodParameters '{' BooleanExpression '}';
MethodParameters = '(' [ MethodParameter { ',' MethodParameter } ] ')';
MethodParameter = StringVariableMethodParamer | NumberVariableMethodParamer | BooleanVariableMethodParamer;
AnnotationParameter = StringVariable | BooleanVariable | NumberVariable;
--FormulaTokenizer-- = (? the special tokenizer for fomula separates the token with white space , BlockComennt , CPPComment , Import , VariableDeclaration and Annotation ?);
```


# Lisence

This project is licensed under the MIT License, see the LICENSE.txt file for details

