package org.unlaxer.util.digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5 {
  
  static ThreadLocal<MessageDigest> messageDigest = new ThreadLocal<>() {
    @Override
    public MessageDigest initialValue() {
      try {
        return MessageDigest.getInstance("MD5");
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
    }
  };
  
  public static byte[] toBytes(byte[] bytes) {
    messageDigest.get().update(bytes);
    return messageDigest.get().digest();
  }
  
  public static byte[] toBytes(String utf8String) {
    return toBytes(utf8String.getBytes(StandardCharsets.UTF_8));
  }
  
  public static String toHex(byte[] bytes) {
    byte[] bytes2 = toBytes(bytes);
    return HEX.encode(bytes2);
  }
  
  public static String toHex(String utf8String) {
    return toHex(utf8String.getBytes(StandardCharsets.UTF_8));
  }  
}
