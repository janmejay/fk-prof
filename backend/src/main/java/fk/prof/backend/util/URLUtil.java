package fk.prof.backend.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;

public class URLUtil {
  private static String ENCODING = "UTF-8";

  public static String buildPathWithRequestParams(String basePath, String... requestParams)
      throws UnsupportedEncodingException {
    if(basePath == null) {
      basePath = "";
    }
    StringBuilder builder = new StringBuilder(basePath);
    for(String requestParam: requestParams) {
      builder.append('/').append(encodeParam(requestParam));
    }
    return builder.toString();

  }

  public static String buildPathWithQueryParams(String basePath, Map<String, String> queryParams)
      throws UnsupportedEncodingException {
    if(basePath == null) {
      basePath = "";
    }
    StringBuilder builder = new StringBuilder(basePath);
    if(queryParams.size() > 0) {
      builder.append('?');
      boolean first = true;
      for(Map.Entry<String, String> queryParam: queryParams.entrySet()) {
        if(!first) {
          builder.append('&');
        }
        builder.append(encodeParam(queryParam.getKey())).append('=').append(encodeParam(queryParam.getValue()));
        first = false;
      }
    }
    return builder.toString();
  }

  private static String encodeParam(String rawParam)
      throws UnsupportedEncodingException {
    return URLEncoder.encode(rawParam, ENCODING);
  }
}
