// Copyright 2010-2018, Google Inc.
// Simplified version for SimpleKeyboard

package org.mozc.android.inputmethod.japanese.nativecallback;

import android.util.Log;

import java.io.IOException;

/**
 * A minimal stub http client class for native layer.
 *
 * This is a stub implementation that does nothing.
 * The Mozc native library expects this class to exist for JNI registration,
 * but we don't actually use HTTP functionality.
 */
public class HttpClient {
  private static final String TAG = "HttpClient";

  private HttpClient() {
  }

  /**
   * Processes a request (typically) given from native layer.
   * This is a stub implementation that always returns empty response.
   *
   * @param method "GET", "POST" or "HEAD".
   * @param url a URL string.
   * @param postData a byte array for data of POST method or null for GET or HEAD methods
   * @return an byte array of the result (empty in this stub)
   */
  public static byte[] request(byte[] method, byte[] url, byte[] postData) throws IOException {
    Log.w(TAG, "HttpClient.request() called but not implemented (stub)");
    // Return empty response
    return new byte[0];
  }
}
