package com.niyarin.mozc.models

/**
 * 新規作成ファイル
 *
 * Mozcライブラリの共通データモデル定義
 */

// ========================================
// Result型（成功/失敗の表現）
// ========================================
sealed class MozcResult<out T> {
    data class Success<T>(val data: T) : MozcResult<T>()
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val errorCode: MozcErrorCode = MozcErrorCode.UNKNOWN
    ) : MozcResult<Nothing>()

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw MozcException(message, cause, errorCode)
    }

    inline fun <R> map(transform: (T) -> R): MozcResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun <R> flatMap(transform: (T) -> MozcResult<R>): MozcResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }
}

enum class MozcErrorCode {
    UNKNOWN,
    INITIALIZATION_FAILED,
    SESSION_CREATION_FAILED,
    COMMAND_EXECUTION_FAILED,
    INVALID_RESPONSE,
    LIBRARY_NOT_LOADED
}

class MozcException(
    message: String,
    cause: Throwable? = null,
    val errorCode: MozcErrorCode = MozcErrorCode.UNKNOWN
) : Exception(message, cause)

// ========================================
// 候補情報
// ========================================
data class Candidate(
    val id: Int,
    val value: String,
    val annotation: String = "",
    val description: String = ""
) {
    companion object {
        fun fromLegacy(id: Int, value: String, annotation: String = ""): Candidate {
            return Candidate(id, value, annotation)
        }
    }
}

// ========================================
// 変換結果
// ========================================
data class ConversionResult(
    val preedit: String,
    val candidates: List<Candidate>,
    val focusedIndex: Int = 0,
    val hasCandidates: Boolean = candidates.isNotEmpty()
) {
    fun getFocusedCandidate(): Candidate? {
        return candidates.getOrNull(focusedIndex)
    }
}

// ========================================
// Mozc設定
// ========================================
data class MozcConfig(
    /**
     * 1ページあたりの候補数
     */
    val candidatePageSize: Int = 20,

    /**
     * テキスト削除機能を有効にするか
     */
    val textDeletionCapability: Boolean = true,

    /**
     * デバッグログを有効にするか
     */
    val enableDebugLog: Boolean = false
) {
    companion object {
        val DEFAULT = MozcConfig()
    }
}

// ========================================
// 特殊キー定義
// ========================================
enum class SpecialKey {
    SPACE,
    ENTER,
    BACKSPACE,
    DELETE,
    ESC,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    TAB
}

// ========================================
// セッション状態
// ========================================
enum class SessionState {
    NOT_INITIALIZED,
    INITIALIZED,
    ERROR,
    CLOSED
}

// ========================================
// レスポンス情報（内部用）
// ========================================
data class MozcResponse(
    val sessionId: Long,
    val hasOutput: Boolean,
    val preedit: String = "",
    val result: String = "",
    val candidates: List<Candidate> = emptyList()
)
