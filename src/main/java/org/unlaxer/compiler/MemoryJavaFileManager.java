package org.unlaxer.compiler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;

public class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
  private final Map<String, ByteArrayJavaFileObject> outputFileByClassName = new HashMap<>();
  private final Map<String, MemoryJavaFileObjectForClass> inputFileByClassName = new HashMap<>();
  
  private final JavaFileManagerContext javaFileManagerContext;

  public MemoryJavaFileManager(JavaFileManager fileManager , JavaFileManagerContext javaFileManagerContext) {
    super(fileManager);
    this.javaFileManagerContext = javaFileManagerContext;
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
      FileObject sibling) throws IOException {
    ByteArrayJavaFileObject fileObject = new ByteArrayJavaFileObject(className, kind);
    outputFileByClassName.put(className, fileObject);
    return fileObject;
  }

  @Override
  public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException {
    if(kind == Kind.CLASS) {
      MemoryJavaFileObjectForClass memoryJavaFileObjectForClass = inputFileByClassName.get(className);
      if(memoryJavaFileObjectForClass != null) {
        return memoryJavaFileObjectForClass;
      }
    }
    return super.getJavaFileForInput(location, className, kind);
  }

  public Map<String, byte[]> getBytesByName() {
    Map<String, byte[]> classBytes = new HashMap<>();
    for (Map.Entry<String, ByteArrayJavaFileObject> entry : outputFileByClassName.entrySet()) {
      classBytes.put(entry.getKey(), entry.getValue().getBytes());
    }
    return classBytes;
  }
  
  public void setJavaFileOBjectForClass(String className,MemoryJavaFileObjectForClass javaFileObject) {
    inputFileByClassName.put(className , javaFileObject);
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<Kind> kinds, boolean recurse)
      throws IOException {
//    System.out.println("☆彡☆彡☆彡---- "+packageName+" ----☆彡☆彡☆彡"+location.getName());
    
    if(false == javaFileManagerContext.matchForOtherFileManager.test(location)) {
      return List.of();
    }
    
    List<JavaFileObject> result = new ArrayList<JavaFileObject>();
    
    inputFileByClassName.forEach((fullClassName,file)->{
      ClassName className = new ClassName(fullClassName);
      if(packageName.equals(className.packageName())){
        result.add(file);
      }
    });
    super.list(location, packageName, kinds, recurse)
      .forEach(result::add);
    
//    if(result.size()>0) {
//      System.out.println(result.size());
//    }
    
//    result.forEach(c->{
//      if(c.toUri().toASCIIString().contains("jdk")) {
//        
//        System.out.println("packageName:"+packageName);
//      }
//    });
//    System.out.println("◇◇◇◇◇◇;" + result.size() +"/"+ inputFileByClassName.size());
    return result;
  }
}