package org.unlaxer;

import java.util.List;
import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.model.ExpressionModelTest.ASTNodeMapping;

public interface ASTNodeContainer{

//  public Parser setASTNodeKind(Name name ,ASTNodeKind astNodeKind);
//
//  public ASTNodeKind astNodeKind();
//
//  public List<ASTNodeKindAndParser> astNodeKind(Name name);
//
//  public static record ASTNodeKindAndParser(ASTNodeKind astNodeKind,Parser parser) {}

  public Parser setASTNodeMapping(ASTNodeMapping astNodeMapping);

  public Optional<ASTNodeMapping> astNodeMapping();

  public List<ASTNodeMappingAndParser> astNodeMappingAndParsers(Name name);

  public static record ASTNodeMappingAndParser(ASTNodeMapping astNodeMapping,Parser parser) {}

}
