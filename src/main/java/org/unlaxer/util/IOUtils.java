package org.unlaxer.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class IOUtils {
  
  public static String toString(InputStream inputStream) throws IOException {
    return toString(inputStream, StandardCharsets.UTF_8);
  }

  
  public static String toString(InputStream inputStream , Charset charset) throws IOException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    for (int length; (length = inputStream.read(buffer)) != -1; ) {
        result.write(buffer, 0, length);
    }
    return result.toString(charset);
  }
  
  public static String toStringUnChecked(InputStream inputStream)  {
    return toStringUnChecked(inputStream, StandardCharsets.UTF_8);
  }
  
  public static String toStringUnChecked(InputStream inputStream , Charset charset)  {
    try(inputStream;ByteArrayOutputStream result = new ByteArrayOutputStream();) {
      
      byte[] buffer = new byte[1024];
      for (int length; (length = inputStream.read(buffer)) != -1; ) {
          result.write(buffer, 0, length);
      }
      return result.toString(charset);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
