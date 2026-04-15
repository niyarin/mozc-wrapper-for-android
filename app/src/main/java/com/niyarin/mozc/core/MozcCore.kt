package com.niyarin.mozc.core

import android.util.Log
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI
import com.niyarin.mozc.models.MozcErrorCode
import com.niyarin.mozc.models.MozcException
import com.niyarin.mozc.models.MozcResult
import org.mozc.android.inputmethod.japanese.nativecallback.HttpClient
import java.io.File

/**
 * 新規作成ファイル
 *
 * Layer 1: Core - MozcJNIへの薄いラッパー
 *
 * 外部ファイルへの依存:
 * - com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI (前回新規作成)
 * - org.mozc.android.inputmethod.japanese.nativecallback.HttpClient (MozcForAndroidからコピー)
 */

private const val TAG = "MozcCore"

/**
 * Mozcの低レベルAPI
 * JNIへの直接アクセスを提供
 */
interface MozcCore {
    /**
     * Mozcコマンドを実行
     * @param command Protocol Buffer形式のコマンド
     * @return Protocol Buffer形式のレスポンス
     */
    fun evalCommand(command: ByteArray): ByteArray

    /**
     * Coreをクローズ
     */
    fun close()

    /**
     * 初期化済みかどうか
     */
    fun isLoaded(): Boolean
}

/**
 * MozcCoreの実装
 */
class MozcCoreImpl private constructor(
    private val userProfileDirectory: File,
    private val dataFile: File?,
    private val enableDebugLog: Boolean
) : MozcCore {

    private var isLibraryLoaded = false

    companion object {
        @Volatile
        private var instance: MozcCoreImpl? = null

        /**
         * HttpClientクラスがビルドツールによって削除されないようにする
         * このクラスはネイティブコードからJNI経由で呼ばれる
         *
         * 参考元: MozcSessionHandler.kt の ensureHttpClientLoaded()
         */
        @JvmStatic
        private fun ensureHttpClientLoaded() {
            try {
                HttpClient.request(byteArrayOf(), byteArrayOf(), null)
            } catch (e: Exception) {
                // Ignored - just ensuring the class is linked
            }
        }

        /**
         * MozcCoreインスタンスを作成
         * シングルトンパターンを使用
         */
        fun create(
            userProfileDirectory: File,
            dataFile: File? = null,
            enableDebugLog: Boolean = false
        ): MozcResult<MozcCore> {
            return try {
                synchronized(this) {
                    if (instance == null) {
                        val core = MozcCoreImpl(userProfileDirectory, dataFile, enableDebugLog)
                        core.initialize()
                        instance = core
                    }
                    MozcResult.Success(instance!!)
                }
            } catch (e: MozcException) {
                logError("Failed to create MozcCore", e)
                MozcResult.Error(
                    "Failed to create MozcCore: ${e.message}",
                    e,
                    e.errorCode
                )
            }
        }

        private fun logDebug(message: String) {
            Log.d(TAG, message)
        }

        private fun logError(message: String, e: Throwable? = null) {
            if (e != null) {
                Log.e(TAG, message, e)
            } else {
                Log.e(TAG, message)
            }
        }
    }

    private fun initialize() {
        if (isLibraryLoaded) {
            return
        }

        // Create user profile directory if it doesn't exist
        if (!userProfileDirectory.exists()) {
            if (!userProfileDirectory.mkdirs()) {
                throw MozcException(
                    "Failed to create user profile directory: ${userProfileDirectory.absolutePath}",
                    null,
                    MozcErrorCode.INITIALIZATION_FAILED
                )
            }
        }

        if (enableDebugLog) {
            logDebug("Initializing Mozc library")
            logDebug("User profile directory: ${userProfileDirectory.absolutePath}")
            logDebug("Data file: ${dataFile?.absolutePath ?: "null"}")
        }

        try {
            // Ensure HttpClient is loaded
            ensureHttpClientLoaded()

            // Load Mozc native library
            MozcJNI.load(userProfileDirectory.absolutePath, dataFile?.absolutePath)

            isLibraryLoaded = true

            if (enableDebugLog) {
                logDebug("Mozc library loaded successfully")
            }
        } catch (e: RuntimeException) {
            logError("Failed to load Mozc library", e)
            throw MozcException(
                "Failed to load Mozc library: ${e.message}",
                e,
                MozcErrorCode.LIBRARY_NOT_LOADED
            )
        }
    }

    override fun evalCommand(command: ByteArray): ByteArray {
        if (!isLibraryLoaded) {
            throw MozcException(
                "Mozc library is not loaded",
                null,
                MozcErrorCode.LIBRARY_NOT_LOADED
            )
        }

        if (enableDebugLog) {
            logDebug("Executing command: ${command.size} bytes")
        }

        return try {
            val response = MozcJNI.evalCommand(command)
            if (enableDebugLog) {
                logDebug("Received response: ${response?.size ?: 0} bytes")
            }
            response ?: byteArrayOf()
        } catch (e: RuntimeException) {
            logError("Command execution failed", e)
            throw MozcException(
                "Command execution failed: ${e.message}",
                e,
                MozcErrorCode.COMMAND_EXECUTION_FAILED
            )
        }
    }

    override fun close() {
        // MozcJNIにはcloseメソッドがないため、現状では何もしない
        // 将来的にリソース解放が必要になった場合はここに実装
        if (enableDebugLog) {
            logDebug("MozcCore closed")
        }
    }

    override fun isLoaded(): Boolean = isLibraryLoaded
}
