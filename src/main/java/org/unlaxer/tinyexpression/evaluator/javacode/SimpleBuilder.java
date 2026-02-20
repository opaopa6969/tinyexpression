package org.unlaxer.tinyexpression.evaluator.javacode;
import java.util.stream.Stream;


public class SimpleBuilder {

  int index;
  StringBuilder builder;

  int tabSpace = 2;

  public SimpleBuilder() {
    this(0);
  }

  public SimpleBuilder(int index) {
    this(index, new StringBuilder());
  }

  public SimpleBuilder(int index, StringBuilder builder) {
    super();
    this.index = index;
    this.builder = builder;
  }

  public SimpleBuilder incTab() {
    ++index;
    return this;
  }

  public SimpleBuilder decTab() {
    --index;
    return this;

  }

  public SimpleBuilder append(String word) {
    builder.append(word);
    return this;
  }

  public SimpleBuilder withTab(String word) {
    tab();
    builder.append(word);
    return this;
  }


  public SimpleBuilder line(String word) {
    tab();
    append(word + "\n");
    return this;
  }

  public SimpleBuilder lines(String lines) {
    String[] split = lines.split("\n");
    for (String line : split) {
      tab();
      append(line + "\n");
    }
    return this;
  }

  public SimpleBuilder lines(Stream<String> linesStream) {
    linesStream.forEach(this::line);
    return this;
  }

  static byte tabBytes = " ".getBytes()[0];

  private SimpleBuilder tab() {
    byte[] tabs = new byte[index* tabSpace];
    for (int i = 0; i < index * tabSpace; i++) {
      tabs[i] = tabBytes;
    }
    builder.append(new String(tabs));
    return this;
  }


  public SimpleBuilder w(String word) {
    word = word == null ? "" : word;
    append("\"" + word.replaceAll("\"", "\\\"") + "\"");
    return this;
  }

  public SimpleBuilder p(String word) {
    word = word == null ? "" : word;
    append("(" + word + ")");
    return this;
  }

  @Override
  public String toString() {
    return builder.toString();
  }


  public SimpleBuilder n() {
    append("\n");
    return this;
  }
}
