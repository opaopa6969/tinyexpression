package org.unlaxer;

import java.util.Optional;
import java.util.Set;

import org.unlaxer.ast.ASTNodeKind;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.Opecode;

public interface ASTNodeContainer {

  public Parser setASTNodeKind(ASTNodeKind astNodeKind, Opecode... targetOpecodes);

  public ASTNodeKind astNodeKind();
  
  public Parser setOperator(Opecode opecode);
  
  public default Parser setOperand(Opecode... targetOpecodes) {
    return setASTNodeKind(ASTNodeKind.Operand , targetOpecodes);
  }

  public Optional<Opecode> opecode();
  
  public Set<Opecode> targetOpecodes();

//  public Parser setASTNodeKind(Name name ,ASTNodeKind astNodeKind);
//
//  public ASTNodeKind astNodeKind();
//
//  public List<ASTNodeKindAndParser> astNodeKind(Name name);
//
//  public static record ASTNodeKindAndParser(ASTNodeKind astNodeKind,Parser parser) {}

//  public Parser setASTNodeMapping(ASTNodeMapping astNodeMapping);
//
//  public Optional<ASTNodeMapping> astNodeMapping();
//
//  public List<ASTNodeMappingAndParser> astNodeMappingAndParsers(Name name);
//
//  public static record ASTNodeMappingAndParser(ASTNodeMapping astNodeMapping,Parser parser) {}

}
