package com.niyarin.mozc.session

import android.util.Log
import com.niyarin.mozc.core.MozcCore
import com.niyarin.mozc.models.*
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands

/**
 * 新規作成ファイル（既存のMozcSessionHandler.ktから移植）
 *
 * Layer 2: Session - セッション管理と低レベルコマンド
 *
 * 外部ファイルへの依存:
 * - org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands (MozcForAndroidからコピー、改造なし)
 *
 * 移植元:
 * - MozcSessionHandler.kt の以下のメソッド:
 *   - createSession()
 *   - setRequestParameters()
 *   - sendKey()
 *   - sendSpaceKey(), sendBackspaceKey()
 *   - selectCandidate()
 *   - commitText()
 *   - resetContext()
 *   - evalCommand()
 *   - extractCandidates() (リフレクション部分)
 */

private const val TAG = "MozcSession"

/**
 * Mozcセッション管理API
 */
interface MozcSession {
    val sessionId: Long
    val state: SessionState

    fun sendKey(keyCode: Int): MozcResult<MozcResponse>
    fun sendSpecialKey(key: SpecialKey): MozcResult<MozcResponse>
    fun selectCandidate(candidateId: Int): MozcResult<MozcResponse>
    fun commit(): MozcResult<MozcResponse>
    fun reset(): MozcResult<MozcResponse>
    fun close()
}

/**
 * MozcSessionの実装
 */
class MozcSessionImpl(
    private val core: MozcCore,
    private val config: MozcConfig
) : MozcSession {

    override var sessionId: Long = 0
        private set

    override var state: SessionState = SessionState.NOT_INITIALIZED
        private set

    init {
        try {
            createSession()
            setRequestParameters()
            state = SessionState.INITIALIZED
        } catch (e: Exception) {
            logError("Failed to initialize session", e)
            state = SessionState.ERROR
            throw MozcException(
                "Failed to initialize session: ${e.message}",
                e,
                MozcErrorCode.SESSION_CREATION_FAILED
            )
        }
    }

    /**
     * セッションを作成
     * 移植元: MozcSessionHandler.createSession()
     */
    private fun createSession() {
        val inputBuilder = ProtoCommands.Input.newBuilder()
            .setType(ProtoCommands.Input.CommandType.CREATE_SESSION)
            .setCapability(
                ProtoCommands.Capability.newBuilder()
                    .setTextDeletion(ProtoCommands.Capability.TextDeletionCapabilityType.DELETE_PRECEDING_TEXT)
                    .build()
            )

        val command = ProtoCommands.Command.newBuilder()
            .setInput(inputBuilder.build())
            .setOutput(ProtoCommands.Output.getDefaultInstance())
            .build()

        val response = evalCommand(command)
        if (response.hasOutput() && response.output.hasId()) {
            sessionId = response.output.id
            logDebug("Session created with ID: $sessionId")
        } else {
            throw MozcException(
                "Failed to create session: response has no output or ID",
                null,
                MozcErrorCode.SESSION_CREATION_FAILED
            )
        }
    }

    /**
     * リクエストパラメータを設定
     * 移植元: MozcSessionHandler.setRequestParameters()
     */
    private fun setRequestParameters() {
        try {
            val request = ProtoCommands.Request.newBuilder()
                .setCandidatePageSize(config.candidatePageSize)
                .build()

            val input = ProtoCommands.Input.newBuilder()
                .setType(ProtoCommands.Input.CommandType.SET_REQUEST)
                .setId(sessionId)
                .setRequest(request)
                .build()

            val command = ProtoCommands.Command.newBuilder()
                .setInput(input)
                .setOutput(ProtoCommands.Output.getDefaultInstance())
                .build()

            evalCommand(command)
            logDebug("Request parameters set: candidatePageSize=${config.candidatePageSize}")
        } catch (e: Exception) {
            logError("Failed to set request parameters", e)
            // 失敗してもセッションは続行できる
        }
    }

    /**
     * キーコードを送信
     * 移植元: MozcSessionHandler.sendKey()
     */
    override fun sendKey(keyCode: Int): MozcResult<MozcResponse> {
        return try {
            val keyEvent = ProtoCommands.KeyEvent.newBuilder()
                .setKeyCode(keyCode)
                .build()

            val command = ProtoCommands.Command.newBuilder()
                .setInput(
                    ProtoCommands.Input.newBuilder()
                        .setType(ProtoCommands.Input.CommandType.SEND_KEY)
                        .setId(sessionId)
                        .setKey(keyEvent)
                        .build()
                )
                .setOutput(ProtoCommands.Output.getDefaultInstance())
                .build()

            val response = evalCommand(command)
            MozcResult.Success(convertToMozcResponse(response))
        } catch (e: Exception) {
            logError("sendKey failed", e)
            MozcResult.Error(
                "sendKey failed: ${e.message}",
                e,
                MozcErrorCode.COMMAND_EXECUTION_FAILED
            )
        }
    }

    /**
     * 特殊キーを送信
     * 移植元: MozcSessionHandler.sendSpaceKey(), sendBackspaceKey() など
     */
    override fun sendSpecialKey(key: SpecialKey): MozcResult<MozcResponse> {
        return try {
            val specialKeyType = when (key) {
                SpecialKey.SPACE -> ProtoCommands.KeyEvent.SpecialKey.SPACE
                SpecialKey.ENTER -> ProtoCommands.KeyEvent.SpecialKey.ENTER
                SpecialKey.BACKSPACE -> ProtoCommands.KeyEvent.SpecialKey.BACKSPACE
                SpecialKey.DELETE -> ProtoCommands.KeyEvent.SpecialKey.DEL
                SpecialKey.ESC -> ProtoCommands.KeyEvent.SpecialKey.ESCAPE
                SpecialKey.LEFT -> ProtoCommands.KeyEvent.SpecialKey.LEFT
                SpecialKey.RIGHT -> ProtoCommands.KeyEvent.SpecialKey.RIGHT
                SpecialKey.UP -> ProtoCommands.KeyEvent.SpecialKey.UP
                SpecialKey.DOWN -> ProtoCommands.KeyEvent.SpecialKey.DOWN
                SpecialKey.TAB -> ProtoCommands.KeyEvent.SpecialKey.TAB
            }

            val keyEvent = ProtoCommands.KeyEvent.newBuilder()
                .setSpecialKey(specialKeyType)
                .build()

            val command = ProtoCommands.Command.newBuilder()
                .setInput(
                    ProtoCommands.Input.newBuilder()
                        .setType(ProtoCommands.Input.CommandType.SEND_KEY)
                        .setId(sessionId)
                        .setKey(keyEvent)
                        .build()
                )
                .setOutput(ProtoCommands.Output.getDefaultInstance())
                .build()

            val response = evalCommand(command)
            MozcResult.Success(convertToMozcResponse(response))
        } catch (e: Exception) {
            logError("sendSpecialKey failed", e)
            MozcResult.Error(
                "sendSpecialKey failed: ${e.message}",
                e,
                MozcErrorCode.COMMAND_EXECUTION_FAILED
            )
        }
    }

    /**
     * 候補を選択
     * 移植元: MozcSessionHandler.selectCandidate()
     */
    override fun selectCandidate(candidateId: Int): MozcResult<MozcResponse> {
        return try {
            val command = ProtoCommands.Command.newBuilder()
                .setInput(
                    ProtoCommands.Input.newBuilder()
                        .setType(ProtoCommands.Input.CommandType.SEND_COMMAND)
                        .setId(sessionId)
                        .setCommand(
                            ProtoCommands.SessionCommand.newBuilder()
                                .setType(ProtoCommands.SessionCommand.CommandType.SELECT_CANDIDATE)
                                .setId(candidateId)
                                .build()
                        )
                        .build()
                )
                .setOutput(ProtoCommands.Output.getDefaultInstance())
                .build()

            val response = evalCommand(command)
            MozcResult.Success(convertToMozcResponse(response))
        } catch (e: Exception) {
            logError("selectCandidate failed", e)
            MozcResult.Error(
                "selectCandidate failed: ${e.message}",
                e,
                MozcErrorCode.COMMAND_EXECUTION_FAILED
            )
        }
    }

    /**
     * テキストを確定
     * 移植元: MozcSessionHandler.commitText()
     */
    override fun commit(): MozcResult<MozcResponse> {
        return sendSpecialKey(SpecialKey.ENTER)
    }

    /**
     * コンテキストをリセット
     * 移植元: MozcSessionHandler.resetContext()
     */
    override fun reset(): MozcResult<MozcResponse> {
        return try {
            val command = ProtoCommands.Command.newBuilder()
                .setInput(
                    ProtoCommands.Input.newBuilder()
                        .setType(ProtoCommands.Input.CommandType.SEND_COMMAND)
                        .setId(sessionId)
                        .setCommand(
                            ProtoCommands.SessionCommand.newBuilder()
                                .setType(ProtoCommands.SessionCommand.CommandType.REVERT)
                                .build()
                        )
                        .build()
                )
                .setOutput(ProtoCommands.Output.getDefaultInstance())
                .build()

            val response = evalCommand(command)
            MozcResult.Success(convertToMozcResponse(response))
        } catch (e: Exception) {
            logError("reset failed", e)
            MozcResult.Error(
                "reset failed: ${e.message}",
                e,
                MozcErrorCode.COMMAND_EXECUTION_FAILED
            )
        }
    }

    override fun close() {
        state = SessionState.CLOSED
        logDebug("Session closed")
    }

    /**
     * コマンドを実行
     * 移植元: MozcSessionHandler.evalCommand()
     */
    private fun evalCommand(command: ProtoCommands.Command): ProtoCommands.Command {
        val inBytes = command.toByteArray()
        logDebug("Sending command: type=${command.input.type}, size=${inBytes.size} bytes")

        val outBytes = core.evalCommand(inBytes)
        logDebug("Received response: size=${outBytes.size} bytes")

        return try {
            val response = ProtoCommands.Command.parseFrom(outBytes)
            logDebug("Parsed response: hasOutput=${response.hasOutput()}")
            response
        } catch (e: Exception) {
            logError("Failed to parse command response", e)
            throw MozcException(
                "Failed to parse response: ${e.message}",
                e,
                MozcErrorCode.INVALID_RESPONSE
            )
        }
    }

    /**
     * ProtoCommands.CommandをMozcResponseに変換し、候補を抽出
     * 移植元: MozcSessionHandler.extractCandidates()
     */
    private fun convertToMozcResponse(response: ProtoCommands.Command): MozcResponse {
        val hasOutput = response.hasOutput()
        logDebug("convertToMozcResponse: hasOutput=$hasOutput")
        if (!hasOutput) {
            return MozcResponse(sessionId, false)
        }

        val output = response.output
        logDebug(
            "convertToMozcResponse: hasPreedit=${output.hasPreedit()}, " +
                "hasResult=${output.hasResult()}, " +
                "hasCandidateWindow=${output.hasCandidateWindow()}, " +
                "hasAllCandidateWords=${output.hasAllCandidateWords()}"
        )
        val candidates = extractCandidates(output)

        val preedit = if (output.hasPreedit()) {
            val segments = mutableListOf<String>()
            for (i in 0 until output.preedit.segmentCount) {
                val segment = output.preedit.getSegment(i)
                if (segment.hasValue()) {
                    segments.add(segment.value)
                }
            }
            segments.joinToString("")
        } else ""

        val result = if (output.hasResult()) {
            output.result.value
        } else ""

        logDebug("convertToMozcResponse: candidates=${candidates.size}")

        return MozcResponse(
            sessionId = sessionId,
            hasOutput = true,
            preedit = preedit,
            result = result,
            candidates = candidates
        )
    }

    /**
     * 候補を抽出
     * 移植元: MozcSessionHandler.extractCandidates()
     *
     * フォールバック順序（MozcSessionHandlerと同じ）:
     * 1. candidate_window
     * 2. all_candidate_words
     */
    private fun extractCandidates(output: ProtoCommands.Output): List<Candidate> {
        val candidates = mutableListOf<Candidate>()

        logDebug("extractCandidates: Starting candidate extraction")

        // 1. Try candidate_window first
        if (output.hasCandidateWindow()) {
            val candidateWindow = output.candidateWindow
            val candidateCount = candidateWindow.candidateCount

            logDebug("extractCandidates: candidateCount from candidate_window = $candidateCount")

            for (i in 0 until candidateCount) {
                val candidate = candidateWindow.getCandidate(i)
                if (candidate.hasValue()) {
                    val id = if (candidate.hasId()) candidate.id else i
                    candidates.add(Candidate.fromLegacy(id, candidate.value))
                    logDebug("extractCandidates: Added candidate[$i]: ${candidate.value} (id=$id)")
                }
            }
        }

        // 2. Try all_candidate_words (MozcSessionHandlerと同じ順序)
        if (candidates.isEmpty() && output.hasAllCandidateWords()) {
            try {
                val allCandidateWords = output.allCandidateWords
                val candidateCount = allCandidateWords.candidatesCount

                logDebug("extractCandidates: candidateCount from all_candidate_words = $candidateCount")

                for (i in 0 until candidateCount) {
                    val candidate = allCandidateWords.getCandidates(i)
                    if (candidate.hasValue()) {
                        candidates.add(Candidate.fromLegacy(candidate.id, candidate.value))
                        logDebug("extractCandidates: Added all_candidate_words[$i]: ${candidate.value} (id=${candidate.id})")
                    }
                }
            } catch (e: Exception) {
                logError("Failed to extract from all_candidate_words", e)
            }
        }

        logDebug("Total candidates extracted: ${candidates.size}")
        return candidates
    }

    private fun logDebug(message: String) {
        if (config.enableDebugLog) {
            Log.d(TAG, message)
        }
    }

    private fun logError(message: String, e: Throwable? = null) {
        if (e != null) {
            Log.e(TAG, message, e)
        } else {
            Log.e(TAG, message)
        }
    }
}
