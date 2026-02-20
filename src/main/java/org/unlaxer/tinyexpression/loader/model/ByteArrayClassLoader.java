package org.unlaxer.tinyexpression.loader.model;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.stream.Stream;

public class ByteArrayClassLoader extends ClassLoader{
  
  ClassLoader parent;

  public ByteArrayClassLoader(ClassLoader parent) {
    super();
    this.parent = parent;
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return super.getName();
  }

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    // TODO Auto-generated method stub
    return super.loadClass(name);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    // TODO Auto-generated method stub
    return super.loadClass(name, resolve);
  }

  @Override
  protected Object getClassLoadingLock(String className) {
    // TODO Auto-generated method stub
    return super.getClassLoadingLock(className);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    // TODO Auto-generated method stub
    return super.findClass(name);
  }

  @Override
  protected Class<?> findClass(String moduleName, String name) {
    // TODO Auto-generated method stub
    return super.findClass(moduleName, name);
  }

  @Override
  protected URL findResource(String moduleName, String name) throws IOException {
    // TODO Auto-generated method stub
    return super.findResource(moduleName, name);
  }

  @Override
  public URL getResource(String name) {
    // TODO Auto-generated method stub
    return super.getResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    // TODO Auto-generated method stub
    return super.getResources(name);
  }

  @Override
  public Stream<URL> resources(String name) {
    // TODO Auto-generated method stub
    return super.resources(name);
  }

  @Override
  protected URL findResource(String name) {
    // TODO Auto-generated method stub
    return super.findResource(name);
  }

  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    // TODO Auto-generated method stub
    return super.findResources(name);
  }

  @Override
  public InputStream getResourceAsStream(String name) {
    // TODO Auto-generated method stub
    return super.getResourceAsStream(name);
  }

  @Override
  protected Package definePackage(String name, String specTitle, String specVersion, String specVendor,
      String implTitle, String implVersion, String implVendor, URL sealBase) {
    // TODO Auto-generated method stub
    return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
  }

  @Override
  protected Package getPackage(String name) {
    // TODO Auto-generated method stub
    return super.getPackage(name);
  }

  @Override
  protected Package[] getPackages() {
    // TODO Auto-generated method stub
    return super.getPackages();
  }

  @Override
  protected String findLibrary(String libname) {
    // TODO Auto-generated method stub
    return super.findLibrary(libname);
  }

  @Override
  public void setDefaultAssertionStatus(boolean enabled) {
    // TODO Auto-generated method stub
    super.setDefaultAssertionStatus(enabled);
  }

  @Override
  public void setPackageAssertionStatus(String packageName, boolean enabled) {
    // TODO Auto-generated method stub
    super.setPackageAssertionStatus(packageName, enabled);
  }

  @Override
  public void setClassAssertionStatus(String className, boolean enabled) {
    // TODO Auto-generated method stub
    super.setClassAssertionStatus(className, enabled);
  }

  @Override
  public void clearAssertionStatus() {
    // TODO Auto-generated method stub
    super.clearAssertionStatus();
  }
  
  @SuppressWarnings("unchecked")
  public<T> Class<T> define(String name , byte[] byteCode) {
    return (Class<T>)super.defineClass(name , byteCode, 0, byteCode.length);
  }
  
}