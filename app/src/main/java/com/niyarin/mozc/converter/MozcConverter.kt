package com.niyarin.mozc.converter

import com.niyarin.mozc.models.*
import com.niyarin.mozc.session.MozcSession

/**
 * 新規作成ファイル（既存のMozcSessionHandler.ktから移植）
 *
 * Layer 3: Converter - IME向け高レベルAPI
 *
 * 移植元:
 * - MozcSessionHandler.kt の以下のメソッド:
 *   - convertString() (変換)
 *   - getSuggestions() (予測)
 */

/**
 * Mozc変換高レベルAPI
 * IME実装で使いやすいインターフェース
 */
interface MozcConverter {
    /**
     * ひらがな文字列を漢字変換
     * @param hiragana ひらがな文字列
     * @return 変換結果（候補リスト含む）
     */
    fun convert(hiragana: String): MozcResult<ConversionResult>

    /**
     * 予測候補を取得（サジェスト）
     * @param hiragana ひらがな文字列
     * @return 候補リスト
     */
    fun getSuggestions(hiragana: String): MozcResult<List<Candidate>>

    /**
     * 候補を選択
     * @param candidateId 候補ID
     * @return 選択後の変換結果
     */
    fun selectCandidate(candidateId: Int): MozcResult<ConversionResult>

    /**
     * 確定
     * @return 確定されたテキスト
     */
    fun commit(): MozcResult<String>

    /**
     * リセット
     */
    fun reset(): MozcResult<Unit>

    /**
     * 1文字削除（バックスペース）
     */
    fun backspace(): MozcResult<ConversionResult>
}

/**
 * MozcConverterの実装
 */
class MozcConverterImpl(
    private val session: MozcSession,
    private val suggestionSession: MozcSession
) : MozcConverter {

    /**
     * ひらがな文字列を変換
     * 移植元: MozcSessionHandler.convertString()
     */
    override fun convert(hiragana: String): MozcResult<ConversionResult> {
        // 1. ひらがなを1文字ずつ送信
        for (char in hiragana) {
            when (val result = session.sendKey(char.code)) {
                is MozcResult.Error -> return result
                else -> {} // 継続
            }
        }

        // 2. スペースキーで変換開始
        val firstResult = when (val result = session.sendSpecialKey(SpecialKey.SPACE)) {
            is MozcResult.Success -> result.data
            is MozcResult.Error -> return result
        }

        // 3. 候補が少ない場合は追加でスペースを送って全候補リストを展開
        val firstPreedit = if (firstResult.preedit.isNotEmpty()) firstResult.preedit else firstResult.result
        if (firstResult.candidates.size <= 1 && firstPreedit.isNotEmpty()) {
            when (val result2 = session.sendSpecialKey(SpecialKey.SPACE)) {
                is MozcResult.Success -> {
                    val second = result2.data
                    val secondPreedit = if (second.preedit.isNotEmpty()) second.preedit else second.result
                    return MozcResult.Success(ConversionResult(
                        preedit = if (secondPreedit.isNotEmpty()) secondPreedit else firstPreedit,
                        candidates = if (second.candidates.isNotEmpty()) second.candidates else firstResult.candidates,
                        focusedIndex = 0
                    ))
                }
                is MozcResult.Error -> {} // 失敗しても最初の結果を使う
            }
        }

        return MozcResult.Success(ConversionResult(
            preedit = if (firstResult.preedit.isNotEmpty()) firstResult.preedit else firstResult.result,
            candidates = firstResult.candidates,
            focusedIndex = 0
        ))
    }

    /**
     * 予測候補を取得
     * 移植元: MozcSessionHandler.getSuggestions()
     *
     * 注意: 新しいMozc v3.33では、EXPAND_SUGGESTIONは廃止されているため、
     * 文字を送った直後のレスポンスから候補を取得する
     * 
     * メインのセッション状態に影響を与えないよう、reset() で前の状態を初期化
     */
    override fun getSuggestions(hiragana: String): MozcResult<List<Candidate>> {
        suggestionSession.reset()

        var lastResponse: MozcResponse? = null

        for (char in hiragana) {
            when (val result = suggestionSession.sendKey(char.code)) {
                is MozcResult.Success -> lastResponse = result.data
                is MozcResult.Error -> {
                    suggestionSession.reset()
                    return result
                }
            }
        }

        val candidates = if (lastResponse != null && lastResponse.candidates.isNotEmpty()) {
            lastResponse.candidates
        } else if (lastResponse != null && lastResponse.preedit.isNotEmpty()) {
            listOf(Candidate(
                id = 0,
                value = lastResponse.preedit,
                annotation = ""
            ))
        } else {
            emptyList()
        }

        suggestionSession.reset()
        return MozcResult.Success(candidates)
    }

    /**
     * 候補を選択
     */
    override fun selectCandidate(candidateId: Int): MozcResult<ConversionResult> {
        return when (val result = session.selectCandidate(candidateId)) {
            is MozcResult.Success -> {
                MozcResult.Success(ConversionResult(
                    preedit = result.data.preedit,
                    candidates = result.data.candidates,
                    focusedIndex = 0
                ))
            }
            is MozcResult.Error -> result
        }
    }

    /**
     * 確定
     */
    override fun commit(): MozcResult<String> {
        return when (val result = session.commit()) {
            is MozcResult.Success -> {
                MozcResult.Success(result.data.result)
            }
            is MozcResult.Error -> result
        }
    }

    /**
     * リセット
     */
    override fun reset(): MozcResult<Unit> {
        return when (val result = session.reset()) {
            is MozcResult.Success -> MozcResult.Success(Unit)
            is MozcResult.Error -> result
        }
    }

    /**
     * バックスペース
     */
    override fun backspace(): MozcResult<ConversionResult> {
        return when (val result = session.sendSpecialKey(SpecialKey.BACKSPACE)) {
            is MozcResult.Success -> {
                MozcResult.Success(ConversionResult(
                    preedit = result.data.preedit,
                    candidates = result.data.candidates,
                    focusedIndex = 0
                ))
            }
            is MozcResult.Error -> result
        }
    }
}

/**
 * Kotlin Coroutines対応版（将来の拡張用）
 */
/*
interface MozcConverterAsync {
    suspend fun convert(hiragana: String): MozcResult<ConversionResult>
    suspend fun getSuggestions(hiragana: String): MozcResult<List<Candidate>>
    suspend fun selectCandidate(candidateId: Int): MozcResult<ConversionResult>
    suspend fun commit(): MozcResult<String>
    suspend fun reset(): MozcResult<Unit>
    suspend fun backspace(): MozcResult<ConversionResult>
}

class MozcConverterAsyncImpl(
    private val converter: MozcConverter
) : MozcConverterAsync {
    override suspend fun convert(hiragana: String) = withContext(Dispatchers.IO) {
        converter.convert(hiragana)
    }

    override suspend fun getSuggestions(hiragana: String) = withContext(Dispatchers.IO) {
        converter.getSuggestions(hiragana)
    }

    // ... 他のメソッドも同様
}
*/
