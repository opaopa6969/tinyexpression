package org.unlaxer.tinyexpression.parser;

public interface TypedVariableParser extends VariableParser{

//  public default ExpressionType type(){
//    return typeAsOptional().get();
//  }
//
//  public default RootVariableParser getRootVariableParer() {
//
//    ExpressionType type = type();
//
//    if(type.isString()) {
//    	return Parser.get(StringVariableParser.class);
//    }else if(type.isBoolean()) {
//    	return Parser.get(BooleanVariableParser.class);
//    }else if(type.isNumber()) {
//    	return Parser.get(NumberVariableParser.class);
//    }
//
//    throw new IllegalArgumentException(type.toString());
//  }

}