/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fury.meta;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.apache.fury.collection.Collections;
import org.apache.fury.meta.MetaString.Encoding;
import org.apache.fury.util.Preconditions;
import org.apache.fury.util.StringUtils;

/** Encodes plain text strings into MetaString objects with specified encoding mechanisms. */
public class MetaStringEncoder {
  private final char specialChar1;
  private final char specialChar2;

  /**
   * Creates a MetaStringEncoder with specified special characters used for encoding.
   *
   * @param specialChar1 The first special character used in custom encoding.
   * @param specialChar2 The second special character used in custom encoding.
   */
  public MetaStringEncoder(char specialChar1, char specialChar2) {
    this.specialChar1 = specialChar1;
    this.specialChar2 = specialChar2;
  }

  /**
   * Encodes the input string to MetaString using adaptive encoding, which intelligently chooses the
   * best encoding based on the string's content.
   *
   * @param input The string to encode.
   * @return A MetaString object representing the encoded string.
   */
  public MetaString encode(String input) {
    return encode(input, Encoding.values());
  }

  public MetaString encode(String input, Encoding[] encodings) {
    if (input.isEmpty()) {
      return new MetaString(input, Encoding.UTF_8, specialChar1, specialChar2, new byte[0]);
    }
    if (!StringUtils.isLatin(input.toCharArray())) {
      return new MetaString(
          input,
          Encoding.UTF_8,
          specialChar1,
          specialChar2,
          input.getBytes(StandardCharsets.UTF_8));
    }
    Encoding encoding = computeEncoding(input, encodings);
    return encode(input, encoding);
  }

  /**
   * Encodes the input string to MetaString using specified encoding.
   *
   * @param input The string to encode.
   * @param encoding The encoding to use.
   * @return A MetaString object representing the encoded string.
   */
  public MetaString encode(String input, Encoding encoding) {
    Preconditions.checkArgument(
        input.length() < Short.MAX_VALUE, "Long meta string than 32767 is not allowed");
    if (encoding != Encoding.UTF_8 && !StringUtils.isLatin(input.toCharArray())) {
      throw new IllegalArgumentException("Non-ASCII characters in meta string are not allowed");
    }
    if (input.isEmpty()) {
      return new MetaString(input, Encoding.UTF_8, specialChar1, specialChar2, new byte[0]);
    }
    byte[] bytes;
    switch (encoding) {
      case LOWER_SPECIAL:
        bytes = encodeLowerSpecial(input);
        return new MetaString(input, encoding, specialChar1, specialChar2, bytes);
      case LOWER_UPPER_DIGIT_SPECIAL:
        bytes = encodeLowerUpperDigitSpecial(input);
        return new MetaString(input, encoding, specialChar1, specialChar2, bytes);
      case FIRST_TO_LOWER_SPECIAL:
        bytes = encodeFirstToLowerSpecial(input);
        return new MetaString(input, encoding, specialChar1, specialChar2, bytes);
      case ALL_TO_LOWER_SPECIAL:
        char[] chars = input.toCharArray();
        int upperCount = countUppers(chars);
        bytes = encodeAllToLowerSpecial(chars, upperCount);
        return new MetaString(input, encoding, specialChar1, specialChar2, bytes);
      default:
        bytes = input.getBytes(StandardCharsets.UTF_8);
        return new MetaString(input, Encoding.UTF_8, specialChar1, specialChar2, bytes);
    }
  }

  public Encoding computeEncoding(String input) {
    return computeEncoding(input, Encoding.values());
  }

  public Encoding computeEncoding(String input, Encoding[] encodings) {
    HashSet<Encoding> encodingSet = Collections.ofHashSet(encodings);
    if (input.isEmpty()) {
      if (encodingSet.contains(Encoding.LOWER_SPECIAL)) {
        return Encoding.LOWER_SPECIAL;
      }
    }
    char[] chars = input.toCharArray();
    StringStatistics statistics = computeStatistics(chars);
    if (statistics.canLowerSpecialEncoded) {
      if (encodingSet.contains(Encoding.LOWER_SPECIAL)) {
        return Encoding.LOWER_SPECIAL;
      }
    }
    if (statistics.canLowerUpperDigitSpecialEncoded) {
      if (statistics.digitCount != 0) {
        if (encodingSet.contains(Encoding.LOWER_UPPER_DIGIT_SPECIAL)) {
          return Encoding.LOWER_UPPER_DIGIT_SPECIAL;
        }
      }
      int upperCount = statistics.upperCount;
      if (upperCount == 1 && Character.isUpperCase(chars[0])) {
        if (encodingSet.contains(Encoding.FIRST_TO_LOWER_SPECIAL)) {
          return Encoding.FIRST_TO_LOWER_SPECIAL;
        }
      }
      if ((chars.length + upperCount) * 5 < (chars.length * 6)) {
        if (encodingSet.contains(Encoding.ALL_TO_LOWER_SPECIAL)) {
          return Encoding.ALL_TO_LOWER_SPECIAL;
        }
      }
      if (encodingSet.contains(Encoding.LOWER_UPPER_DIGIT_SPECIAL)) {
        return Encoding.LOWER_UPPER_DIGIT_SPECIAL;
      }
    }
    return Encoding.UTF_8;
  }

  private static class StringStatistics {
    final int digitCount;
    final int upperCount;
    final boolean canLowerUpperDigitSpecialEncoded;
    final boolean canLowerSpecialEncoded;

    public StringStatistics(
        int digitCount,
        int upperCount,
        boolean canLowerSpecialEncoded,
        boolean canLowerUpperDigitSpecialEncoded) {
      this.digitCount = digitCount;
      this.upperCount = upperCount;
      this.canLowerSpecialEncoded = canLowerSpecialEncoded;
      this.canLowerUpperDigitSpecialEncoded = canLowerUpperDigitSpecialEncoded;
    }
  }

  private StringStatistics computeStatistics(char[] chars) {
    boolean canLowerUpperDigitSpecialEncoded = true;
    boolean canLowerSpecialEncoded = true;
    int digitCount = 0;
    int upperCount = 0;
    for (char c : chars) {
      if (canLowerUpperDigitSpecialEncoded) {
        if (!((c >= 'a' && c <= 'z')
            || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9')
            || (c == specialChar1 || c == specialChar2))) {
          // Character outside of LOWER_UPPER_DIGIT_SPECIAL set
          canLowerUpperDigitSpecialEncoded = false;
        }
      }
      if (canLowerSpecialEncoded) {
        if (!((c >= 'a' && c <= 'z') || (c == '.' || c == '_' || c == '$' || c == '|'))) {
          // Character outside of LOWER_SPECIAL set
          canLowerSpecialEncoded = false;
        }
      }
      if (Character.isDigit(c)) {
        digitCount++;
      }
      if (Character.isUpperCase(c)) {
        upperCount++;
      }
    }
    return new StringStatistics(
        digitCount, upperCount, canLowerSpecialEncoded, canLowerUpperDigitSpecialEncoded);
  }

  private int countUppers(char[] chars) {
    int upperCount = 0;
    for (char c : chars) {
      if (Character.isUpperCase(c)) {
        upperCount++;
      }
    }
    return upperCount;
  }

  public byte[] encodeLowerSpecial(String input) {
    return encodeGeneric(input, 5);
  }

  public byte[] encodeLowerUpperDigitSpecial(String input) {
    return encodeGeneric(input, 6);
  }

  public byte[] encodeFirstToLowerSpecial(String input) {
    return encodeFirstToLowerSpecial(input.toCharArray());
  }

  public byte[] encodeFirstToLowerSpecial(char[] chars) {
    chars[0] = Character.toLowerCase(chars[0]);
    return encodeGeneric(chars, 5);
  }

  public byte[] encodeAllToLowerSpecial(char[] chars, int upperCount) {
    char[] newChars = new char[chars.length + upperCount];
    int newIdx = 0;
    for (char c : chars) {
      if (Character.isUpperCase(c)) {
        newChars[newIdx++] = '|';
        newChars[newIdx++] = Character.toLowerCase(c);
      } else {
        newChars[newIdx++] = c;
      }
    }
    return encodeGeneric(newChars, 5);
  }

  private byte[] encodeGeneric(String input, int bitsPerChar) {
    return encodeGeneric(input.toCharArray(), bitsPerChar);
  }

  private byte[] encodeGeneric(char[] chars, int bitsPerChar) {
    int totalBits = chars.length * bitsPerChar + 1;
    int byteLength = (totalBits + 7) / 8; // Calculate number of needed bytes
    byte[] bytes = new byte[byteLength];
    int currentBit = 1;
    for (char c : chars) {
      int value =
          (bitsPerChar == 5) ? charToValueLowerSpecial(c) : charToValueLowerUpperDigitSpecial(c);
      // Encode the value in bitsPerChar bits
      for (int i = bitsPerChar - 1; i >= 0; i--) {
        if ((value & (1 << i)) != 0) {
          // Set the bit in the byte array
          int bytePos = currentBit / 8;
          int bitPos = currentBit % 8;
          bytes[bytePos] |= (byte) (1 << (7 - bitPos));
        }
        currentBit++;
      }
    }
    boolean stripLastChar = bytes.length * 8 >= totalBits + bitsPerChar;
    if (stripLastChar) {
      bytes[0] = (byte) (bytes[0] | 0x80);
    }
    return bytes;
  }

  private int charToValueLowerSpecial(char c) {
    if (c >= 'a' && c <= 'z') {
      return c - 'a';
    } else if (c == '.') {
      return 26;
    } else if (c == '_') {
      return 27;
    } else if (c == '$') {
      return 28;
    } else if (c == '|') {
      return 29;
    } else {
      throw new IllegalArgumentException("Unsupported character for LOWER_SPECIAL encoding: " + c);
    }
  }

  private int charToValueLowerUpperDigitSpecial(char c) {
    if (c >= 'a' && c <= 'z') {
      return c - 'a';
    } else if (c >= 'A' && c <= 'Z') {
      return 26 + (c - 'A');
    } else if (c >= '0' && c <= '9') {
      return 52 + (c - '0');
    } else if (c == specialChar1) {
      return 62;
    } else if (c == specialChar2) {
      return 63;
    } else {
      throw new IllegalArgumentException(
          "Unsupported character for LOWER_UPPER_DIGIT_SPECIAL encoding: " + c);
    }
  }
}
