package fk.prof.metrics;

public class Util {
  public static String encodeTags(String... tags) {
    StringBuilder result = new StringBuilder();
    boolean first = true;
    for(String tag: tags) {
      if(!first) {
        result.append('_');
      }
      result.append(encodeTag(tag));
      first = false;
    }
    return result.toString();
  }

  private static String encodeTag(String tag) {
    if(tag == null) {
      return null;
    }

    StringBuilder result = new StringBuilder();
    for(int i = 0; i < tag.length(); i++) {
      char ch = tag.charAt(i);
      int ascii = (int) ch;
      if(ascii > 255) {
        // Dropping char outside standard ASCII range, because we use 2-length hex code for encoding.
        // Beyond 255, it takes more than 2-length hex code to uniquely identify a character.
        continue;
      }
      if((ascii >= 48 && ascii <= 57) || (ascii >= 65 && ascii <= 90) || (ascii >= 97 && ascii <= 122)) {
        result.append(ch);
      } else {
        result.append(String.format(".%02X", ascii));
      }
    }
    return result.toString();
  }
}
