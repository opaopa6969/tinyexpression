package org.unlaxer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringUtils {

  /**
   * from commons.lang3
   * <p>
   * Checks if a CharSequence is whitespace, empty ("") or null.
   * </p>
   *
   * <pre>
   * StringUtils.isBlank(null)      = true
   * StringUtils.isBlank("")        = true
   * StringUtils.isBlank(" ")       = true
   * StringUtils.isBlank("bob")     = false
   * StringUtils.isBlank("  bob  ") = false
   * </pre>
   *
   * @param cs the CharSequence to check, may be null
   * @return {@code true} if the CharSequence is null, empty or whitespace
   * @since 2.0
   * @since 3.0 Changed signature from isBlank(String) to isBlank(CharSequence)
   */
  public static boolean isBlank(CharSequence cs) {
    int strLen;
    if (cs == null || (strLen = cs.length()) == 0) {
      return true;
    }
    for (int i = 0; i < strLen; i++) {
      if ((Character.isWhitespace(cs.charAt(i)) == false)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isNoneBlank(CharSequence cs) {

    return false == isBlank(cs);
  }

  public static String from(InputStream inputStream , Charset charset) {
    try (inputStream){
      return new String(inputStream.readAllBytes(), charset);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
