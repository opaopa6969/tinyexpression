package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.SingleCharacterParser;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstField;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstNode;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstNodeKind;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstOperator;

@TinyAstNode(kind = TinyAstNodeKind.NUMBER_BINARY)
@TinyAstOperator(symbol = "+")
@TinyAstField(name = "left", childIndex = 1)
@TinyAstField(name = "right", childIndex = 2)
public class PlusParser extends SingleCharacterParser implements NumberExpression {
	
	private static final long serialVersionUID = -2284625778872306935L;
	
	public static PlusParser SINGLETON = new PlusParser();

	@Override
	public boolean isMatch(char target) {
		return '+' == target; 
	}
}
