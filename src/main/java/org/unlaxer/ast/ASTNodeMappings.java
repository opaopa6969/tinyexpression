package org.unlaxer.ast;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.Token.ScanDirection;
import org.unlaxer.parser.Parser;

public class ASTNodeMappings{

    LinkedHashMap<Name, ASTNodeMapping> mappingByName = new LinkedHashMap<>();
    Map<ASTNodeMapping,Parser> parserByAstNodeMapping = new HashMap<>();
    Map<Parser,ASTNodeMapping> astNodeMappingByParser = new HashMap<>();

    public ASTNodeMappings(ASTNodeMapping... astNodeMappings) {
      super();
      for (ASTNodeMapping astNodeMapping : astNodeMappings) {
        astNodeMapping.setAstNodeMappings(this);
        mappingByName.put(astNodeMapping.name(), astNodeMapping);
      }
    }

    public ASTNodeMapping get(Name name) {
      return mappingByName.get(name);
    }

    public void setParser(ASTNodeMapping astNodeMapping , Parser parser) {
      parserByAstNodeMapping.put(astNodeMapping, parser);
    }

    public boolean isSameGroup(Token... tokens) {

      String parentPath=null;

      for (Token token : tokens) {

        String path = token.getPath();
        Collection<Parser> parsers = parserByAstNodeMapping.values();
        for (Parser parser : parsers) {
          String parserPath = parser.getPath();
          boolean endsWith = path.endsWith(parentPath);
          if(false == endsWith) {
            return false;
          }
          if(parentPath == null) {
            parentPath = path.substring(0, path.length() - parserPath.length());
            continue;
          }
          if(false == parentPath.equals(path.substring(0, path.length() - parserPath.length()))){
            return false;
          }
        }
      }
      return true;
    }


  }