package org.unlaxer.util.digest;

import java.util.HexFormat;

public class HEX{
	
	public enum ByteOrder{
		BIG_ENDIAN,LITTLE_ENDIAN;
	}
	
	private static final char[] LOOKUP_TABLE_LOWER = new char[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66};
	private static final char[] LOOKUP_TABLE_UPPER = new char[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46};
	        
	public static String encode(byte[] byteArray, boolean upperCase, HEX.ByteOrder byteOrder) {

	    // our output size will be exactly 2x byte-array length
	    final char[] buffer = new char[byteArray.length * 2];

	    // choose lower or uppercase lookup table
	    final char[] lookup = upperCase ? LOOKUP_TABLE_UPPER : LOOKUP_TABLE_LOWER;

	    int index;
	    for (int i = 0; i < byteArray.length; i++) {
	        // for little endian we count from last to first
	        index = (byteOrder == ByteOrder.BIG_ENDIAN) ? i : byteArray.length - i - 1;
	        
	        // extract the upper 4 bit and look up char (0-A)
	        buffer[i << 1] = lookup[(byteArray[index] >> 4) & 0xF];
	        // extract the lower 4 bit and look up char (0-A)
	        buffer[(i << 1) + 1] = lookup[(byteArray[index] & 0xF)];
	    }
	    return new String(buffer);
	}

	public static String encode(byte[] byteArray) {
	    return encode(byteArray, true, ByteOrder.BIG_ENDIAN);
	}
	
	public static byte[] decode(String hexString) {
	  return HexFormat.of().parseHex(hexString);
	}
}