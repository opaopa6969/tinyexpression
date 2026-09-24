package org.unlaxer.tinyexpression.p4.ubnfc;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.P4SourceText;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.Json;

/**
 * {@link TinyExpressionP4AST} を {@code {"type","span","fields"}} の canonical JSON にする。
 * span は {@link P4SourceText} から引く（classic / ubnfc のどちらも同じ公開 API で引くので、
 * 片側だけ別の情報源を使うことがない）。移植元: ubnfc examples/p4-java-facade（UBNFC_PIN）。
 */
final class CanonicalAst {

  private CanonicalAst() {}

  static String json(P4PreferredAstMapper.ParsedAst parsed) {
    return json(parsed.ast(), parsed.sourceText());
  }

  static String json(TinyExpressionP4AST ast, P4SourceText sourceText) {
    return Json.write(value(ast, sourceText));
  }

  private static Object value(Object node, P4SourceText sourceText) {
    if (node instanceof Optional<?> optional) {
      return value(optional.orElse(null), sourceText);
    }
    if (node instanceof List<?> list) {
      var out = new ArrayList<Object>(list.size());
      for (Object item : list) {
        out.add(value(item, sourceText));
      }
      return out;
    }
    if (node == null || !node.getClass().isRecord()) {
      return node;
    }
    var fields = new LinkedHashMap<String, Object>();
    for (RecordComponent component : node.getClass().getRecordComponents()) {
      Object child;
      try {
        child = component.getAccessor().invoke(node);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("AST component is not accessible", failure);
      }
      fields.put(component.getName(), value(child, sourceText));
    }
    var out = new LinkedHashMap<String, Object>();
    out.put("type", node.getClass().getSimpleName());
    out.put("span", span(node, sourceText));
    out.put("fields", fields);
    return out;
  }

  private static Object span(Object node, P4SourceText sourceText) {
    Optional<int[]> span;
    try {
      span = sourceText.spanOf(node);
    } catch (RuntimeException invalid) {
      return "invalid";
    }
    return span.<Object>map(offsets -> List.of(offsets[0], offsets[1])).orElse(null);
  }
}
