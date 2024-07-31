package org.unlaxer.compiler;

public class ClassName {
  final String fullName;
  final String name;
  final String packageName;

  public ClassName(String fullName) {
    super();
    this.fullName = fullName;
    int lastIndexOf = fullName.lastIndexOf(".");
    name = (lastIndexOf == -1) ? fullName : fullName.substring(lastIndexOf + 1);
    packageName = (lastIndexOf == -1) ? "" : fullName.substring(0, lastIndexOf);
  }

  public String fullName() {
    return fullName;
  }

  public String name() {
    return name;
  }

  public String packageName() {
    return packageName;
  }
}