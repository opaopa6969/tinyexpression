package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.OneOrMore;
import org.unlaxer.parser.posix.AlphabetNumericUnderScoreParser;

public class NakedVariableParser extends LazyChain implements VariableParser{//implements Expression , BooleanExpression , StringExpression{

	private static final long serialVersionUID = -8533685205048474333L;

	public NakedVariableParser() {
		super();
	}

	public NakedVariableParser(Name name) {
		super(name);
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  
	  return 
      new Parsers(
        Parser.get(DollarParser.class),
        new OneOrMore(
          Parser.get(AlphabetNumericUnderScoreParser.class)
        )
      );
	}
	
	public static String getVariableName(Token thisParserParsed) {
    String variableName = thisParserParsed.tokenString.get().substring(1);
    return variableName; 
	}

  @Override
  public Optional<VariableType> type() {
    return Optional.empty();
  }

}
