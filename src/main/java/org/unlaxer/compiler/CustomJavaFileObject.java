package org.unlaxer.compiler;

import java.net.URI;
import java.io.*;
import javax.tools.JavaFileObject;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.Modifier;

/*
 * modified and repackage below sources for java9 and j2ee container
 * https://atamur.blogspot.com/2009/10/using-built-in-javacompiler-with-custom.html
 * 
 * @author atamur
 * @author opa
 * @since 15-Oct-2009
 */
class CustomJavaFileObject implements JavaFileObject {
 private final String binaryName;
 private final URI uri;
 private final String name;

 public CustomJavaFileObject(String binaryName, URI uri) {
  this.uri = uri;
  this.binaryName = binaryName;
  name = uri.getPath() == null ? uri.getSchemeSpecificPart() : uri.getPath(); // for FS based URI the path is not null, for JAR URI the scheme specific part is not null
 }

 @Override
 public URI toUri() {
  return uri;
 }

 @Override
 public InputStream openInputStream() throws IOException {
  return uri.toURL().openStream(); // easy way to handle any URI!
 }

 @Override
 public OutputStream openOutputStream() throws IOException {
  throw new UnsupportedOperationException();
 }

 @Override
 public String getName() {
  return name;
 }

 @Override
 public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
  throw new UnsupportedOperationException();
 }

 @Override
 public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
  throw new UnsupportedOperationException();
 }

 @Override
 public Writer openWriter() throws IOException {
  throw new UnsupportedOperationException();
 }

 @Override
 public long getLastModified() {
  return 0;
 }

 @Override
 public boolean delete() {
  throw new UnsupportedOperationException();
 }

 @Override
 public Kind getKind() {
  return Kind.CLASS;
 }

 @Override // copied from SimpleJavaFileManager
 public boolean isNameCompatible(String simpleName, Kind kind) {
  String baseName = simpleName + kind.extension;
  return kind.equals(getKind())
    && (baseName.equals(getName())
    || getName().endsWith("/" + baseName));
 }

 @Override
 public NestingKind getNestingKind() {
  throw new UnsupportedOperationException();
 }

 @Override
 public Modifier getAccessLevel() {
  throw new UnsupportedOperationException();
 }

 public String binaryName() {
  return binaryName;
 }


 @Override
 public String toString() {
  return "CustomJavaFileObject{" +
    "uri=" + uri +
    '}';
 }
}