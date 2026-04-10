// Copyright 2010-2021, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.google.android.apps.inputmethod.libs.mozc.session;

import android.util.Log;

/**
 * JNI wrapper for the new Mozc native library (v3.33+)
 * This class bridges between the old API and the new libmozc.so
 */
public class MozcJNI {
  private static final String TAG = "MozcJNI";
  private static volatile boolean isLoaded = false;
  private static volatile boolean isInitialized = false;

  /**
   * Loads and initializes the JNI library.
   *
   * @param userProfileDirectoryPath path to user profile directory
   * @param dataFilePath optional path to data file (e.g., mozc.data), or {@code null}
   */
  public static void load(String userProfileDirectoryPath, String dataFilePath) {
    if (userProfileDirectoryPath == null) {
      throw new NullPointerException("userProfileDirectoryPath cannot be null");
    }

    if (isLoaded) {
      return;
    }
    synchronized (MozcJNI.class) {
      if (isLoaded) {
        return;
      }
      Log.d(TAG, "start MozcJNI#load " + System.nanoTime());
      try {
        System.loadLibrary("mozc");
        Log.v(TAG, "loadLibrary succeeded");
      } catch (Throwable e) {
        Log.e(TAG, "loadLibrary failed", e);
        throw new RuntimeException(e);
      }

      // Call initialize() which registers native methods
      if (!initialize()) {
        throw new RuntimeException("Failed to initialize native methods");
      }
      isInitialized = true;

      if (!onPostLoad(userProfileDirectoryPath, dataFilePath)) {
        Log.e(TAG, "onPostLoad fails");
        return;
      }
      isLoaded = true;
      Log.d(TAG, "end MozcJNI#load " + System.nanoTime());
    }
  }

  /**
   * Initializes the native library and registers native methods.
   * This method is implemented in the new libmozc.so.
   */
  private static native boolean initialize();

  /**
   * Sends Command message to Mozc server and get a result.
   *
   * @param command blob of Command message.
   * @return blob of Command message.
   */
  public static synchronized native byte[] evalCommand(byte[] command);

  /**
   * This method initializes the internal state of mozc server.
   */
  private static synchronized native boolean onPostLoad(
      String userProfileDirectoryPath, String dataFilePath);

  /**
   * @return Data version string currently loaded in native layer.
   */
  public static native String getDataVersion();
}
