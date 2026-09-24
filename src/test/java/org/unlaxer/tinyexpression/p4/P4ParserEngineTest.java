package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Engine resolution order: scoped override > -Dtinyexpression.p4.engine > default (ubnfc). */
public class P4ParserEngineTest {

  private String previous;

  @Before
  public void clearProperty() {
    previous = System.clearProperty(P4ParserEngine.SYSTEM_PROPERTY);
  }

  @After
  public void restoreProperty() {
    if (previous == null) {
      System.clearProperty(P4ParserEngine.SYSTEM_PROPERTY);
    } else {
      System.setProperty(P4ParserEngine.SYSTEM_PROPERTY, previous);
    }
  }

  @Test
  public void defaultIsUbnfc() {
    assertEquals(P4ParserEngine.UBNFC, P4ParserEngine.DEFAULT);
    assertEquals(P4ParserEngine.UBNFC, P4ParserEngine.current());
    assertEquals(P4ParserEngine.UBNFC, P4PreferredAstMapper.engine());
  }

  @Test
  public void propertyOverridesDefaultAndScopeOverridesProperty() {
    System.setProperty(P4ParserEngine.SYSTEM_PROPERTY, " Classic ");
    assertEquals(P4ParserEngine.CLASSIC, P4ParserEngine.current());
    assertEquals(P4ParserEngine.UBNFC,
        P4ParserEngine.with(P4ParserEngine.UBNFC, P4ParserEngine::current));
    assertEquals(P4ParserEngine.CLASSIC, P4ParserEngine.with(null, P4ParserEngine::current));
    assertEquals(P4ParserEngine.CLASSIC, P4ParserEngine.configured());
  }

  @Test
  public void scopesNestAndRestore() {
    P4ParserEngine.with(P4ParserEngine.CLASSIC, () -> {
      assertEquals(P4ParserEngine.CLASSIC, P4ParserEngine.current());
      P4ParserEngine.with(P4ParserEngine.UBNFC,
          () -> assertEquals(P4ParserEngine.UBNFC, P4ParserEngine.current()));
      assertEquals(P4ParserEngine.CLASSIC, P4ParserEngine.current());
    });
    assertEquals(P4ParserEngine.UBNFC, P4ParserEngine.current());
    assertThrows(IllegalStateException.class, () -> P4ParserEngine.with(P4ParserEngine.CLASSIC,
        (Runnable) () -> {
          throw new IllegalStateException("boom");
        }));
    assertEquals("scope restored after a failure", P4ParserEngine.UBNFC, P4ParserEngine.current());
  }

  @Test
  public void deprecatedLegacyAliasResolvesToClassic() {
    assertEquals(Optional.of(P4ParserEngine.CLASSIC), P4ParserEngine.parse("legacy"));
    assertEquals(Optional.of(P4ParserEngine.CLASSIC), P4ParserEngine.parse(" LEGACY "));
    assertEquals(Optional.of(P4ParserEngine.CLASSIC),
        P4ParserEngine.parseStrict("legacy", "test"));
    System.setProperty(P4ParserEngine.SYSTEM_PROPERTY, "legacy");
    assertEquals(P4ParserEngine.CLASSIC, P4ParserEngine.current());
  }

  @Test
  public void unknownValuesAreRejectedNotIgnored() {
    assertEquals(Optional.empty(), P4ParserEngine.parse("combinator"));
    assertEquals(Optional.empty(), P4ParserEngine.parseStrict("  ", "test"));
    assertThrows(IllegalArgumentException.class, () -> P4ParserEngine.parseStrict("combinator", "test"));
    System.setProperty(P4ParserEngine.SYSTEM_PROPERTY, "combinator");
    IllegalArgumentException failure =
        assertThrows(IllegalArgumentException.class, P4ParserEngine::current);
    assertTrue(failure.getMessage(), failure.getMessage().contains(P4ParserEngine.SYSTEM_PROPERTY));
  }

  @Test
  public void facadeDispatchesToTheSelectedEngineWithTheSameResult() {
    String formula = "if($age >= 20){100}else{0}";
    P4PreferredAstMapper.ParsedAst ubnfc = P4ParserEngine.with(P4ParserEngine.UBNFC,
        () -> P4PreferredAstMapper.parseDetailed(formula));
    P4PreferredAstMapper.ParsedAst classic = P4ParserEngine.with(P4ParserEngine.CLASSIC,
        () -> P4PreferredAstMapper.parseDetailed(formula));
    assertEquals(classic.ast(), ubnfc.ast());
    assertEquals(classic.selectionMode(), ubnfc.selectionMode());
  }
}
