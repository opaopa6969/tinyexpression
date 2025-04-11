package org.unlaxer.ast;

import org.unlaxer.Tag;

public enum ASTNodeKind{
//	Operator(true,false,false,false,false,false),
//	Operand(false,true,false,false,false,false),
//	ChoicedOperatorRoot(true,false,true,false,false,true),
//	ChoicedOperator(true,false,true,false,false,false),
//	ChoicedOperandRoot(false,true,true,true,false,true),
//	ChoicedOperand(false,true,true,false,false,false),
//	OperandPredecessor(false,true,false,false,true,false),
//	ZeroOrMoreOperatorOperandSuccessor(false,true,false,false,true,false),
//	OneOrMoreOperatorOperandSuccessor,
//	ZeroOrMoreChoicedOperatorOperandSuccessor,
//	OneOrMoreChoicedOperatorOperandSuccessor,
//	ZeroOrMoreOperandSuccessor,
//	OneOrMoreOperandSuccessor,
//	ZeroOrMoreOperatorSuccessor,
//	OneOrMoreOperatorSuccessor,
  Operator,
  Operand,
  Delimitor,
	Space,
	Comment,
	Annotation,
	AnnotationAttribute,
	Other,
	NotSpecified,
	FoldLeft,
	FoldRight,
	;
  
  Tag tag;
//  String description;
//  boolean operator;
//  boolean operand;
//  boolean choiced;
//  boolean successor;
//  boolean predecessor;
//  boolean root;
	
//	private ASTNodeKind() {
//    this.tag = Tag.of(this);
//    this.description = name();
//  }
//	
//	 private ASTNodeKind(String description) {
//    this.tag = Tag.of(this);
//    this.description = description;
//  }

	 
	 
//  private ASTNodeKind(//
//      boolean operator,//1 
//      boolean operand, //2
//      boolean choiced, //3
//      boolean successor,//4
//      boolean predecessor,//5
//      boolean root//6
//      ) {
//    this.operator = operator;
//    this.operand = operand;
//    this.choiced = choiced;
//    this.successor = successor;
//    this.predecessor = predecessor;
//    this.root = root;
//    this.tag = Tag.of(this);
//    this.description = name();
//  }

  public Tag tag() {
    return tag;
	}
  
//  public String description() {
//    return description;
//  }
//
  public boolean isOperator() {
    return this == Operator;
  }


  public boolean isOperand() {
    return this == Operand;
  }
  
  public static class ASTNodeMeta{
    ASTNodeKind astNodeKind;
    NodeOccurs nodeOccurs;
    
  }

//  public boolean isChoiced() {
//    return choiced;
//  }
//
//  public boolean isSuccessor() {
//    return successor;
//  }
//
//
//  public boolean isPredecessor() {
//    return predecessor;
//  }
//
//  public boolean isRoot() {
//    return root;
//  }
//
  
}