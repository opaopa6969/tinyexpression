package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;
import org.unlaxer.CodePointLength;
import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.TerminalSymbol;
import org.unlaxer.parser.elementary.AbstractTokenParser;
import org.unlaxer.tinyexpression.codeblock.LongCodeFence;

/** Input-only atomic token; failure leaves both cursors and user state unchanged.
 * Like CodeStartParser, inverted recognition cannot match a line boundary.
 */
public class LongCodeBlockParser extends AbstractTokenParser implements TerminalSymbol {
  @Override
  public Parsed parse(ParseContext context, TokenKind kind, boolean inverted) {
    context.startParse(this, context, kind, inverted);
    Token token = getToken(context, kind, inverted);
    Parsed result = Parsed.FAILED;
    if (token.source.isPresent()) {
      context.getCurrent().addToken(token, kind);
      if (kind.isConsumed()) context.consume(token.source.codePointLength());
      else context.matchOnly(token.source.codePointLength());
      result = new Parsed(token);
    }
    // Do not consume(0) on failure: that would reset an independently matched cursor.
    context.endParse(this, result, context, kind, inverted);
    return result;
  }

  @Override
  public Token getToken(ParseContext context, TokenKind kind, boolean inverted) {
    String source = context.getSource().sourceAsString();
    int cp = context.getPosition(kind).value();
    int start = source.offsetByCodePoints(0, cp);
    var layout = inverted ? null : LongCodeFence.scan(source, start);
    return layout == null ? Token.empty(kind, context.getCursor(kind), this)
        : new Token(kind, context.peek(kind,
            new CodePointLength(source.codePointCount(start, layout.end()))), this);
  }

  @Override
  public Optional<String> expectedDisplayText() { return Optional.of("long code block"); }
}
