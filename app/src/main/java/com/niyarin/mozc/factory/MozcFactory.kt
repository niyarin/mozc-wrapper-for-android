package com.niyarin.mozc.factory

import android.content.Context
import com.niyarin.mozc.converter.MozcConverter
import com.niyarin.mozc.converter.MozcConverterImpl
import com.niyarin.mozc.core.MozcCore
import com.niyarin.mozc.core.MozcCoreImpl
import com.niyarin.mozc.models.MozcConfig
import com.niyarin.mozc.models.MozcErrorCode
import com.niyarin.mozc.models.MozcResult
import com.niyarin.mozc.session.MozcSession
import com.niyarin.mozc.session.MozcSessionImpl
import java.io.File

/**
 * 新規作成ファイル
 *
 * Mozcライブラリのファクトリークラス
 * 3層すべてを初期化し、使いやすいAPIを提供
 */

object MozcFactory {

    /**
     * MozcConverterを作成（Androidに依存しない汎用版）
     *
     * @param userProfileDirectory ユーザープロファイルディレクトリ（.mozcなど）
     * @param dataFile Mozcデータファイル（オプション）
     * @param config Mozc設定
     * @return MozcConverterインスタンス
     */
    fun create(
        userProfileDirectory: File,
        dataFile: File? = null,
        config: MozcConfig = MozcConfig.DEFAULT
    ): MozcResult<MozcConverter> {
        return try {
            // Layer 1: Core
            val coreResult = MozcCoreImpl.create(
                userProfileDirectory,
                dataFile,
                config.enableDebugLog
            )
            val core = when (coreResult) {
                is MozcResult.Success -> coreResult.data
                is MozcResult.Error -> return coreResult
            }

            // Layer 2: Session
            val session = MozcSessionImpl(core, config)

            // Layer 3: Converter
            val converter = MozcConverterImpl(session)

            MozcResult.Success(converter)
        } catch (e: Exception) {
            MozcResult.Error(
                "Failed to create MozcConverter: ${e.message}",
                e
            )
        }
    }

    /**
     * MozcConverterを作成（Android Context版）
     *
     * @param context Androidコンテキスト
     * @param dataFile Mozcデータファイル（オプション）
     * @param config Mozc設定
     * @return MozcConverterインスタンス
     */
    fun createFromContext(
        context: Context,
        dataFile: File? = null,
        config: MozcConfig = MozcConfig.DEFAULT
    ): MozcResult<MozcConverter> {
        val userProfileDirectory = File(context.applicationInfo.dataDir, ".mozc")
        return create(userProfileDirectory, dataFile, config)
    }

    /**
     * Mozcデータファイルを外部ストレージからコピー
     *
     * @param context Androidコンテキスト
     * @param dataFilePath データファイルパス
     * @param config Mozc設定
     * @return MozcConverterインスタンス
     */
    fun createFromContextWithDataFile(
        context: Context,
        dataFilePath: String,
        config: MozcConfig = MozcConfig.DEFAULT
    ): MozcResult<MozcConverter> {
        return try {
            val dataFile = File(dataFilePath)
            if (!dataFile.exists()) {
                return MozcResult.Error(
                    "Data file not found: $dataFilePath",
                    null,
                    MozcErrorCode.INITIALIZATION_FAILED
                )
            }
            createFromContext(context, dataFile, config)
        } catch (e: Exception) {
            MozcResult.Error(
                "Failed to load data file: ${e.message}",
                e,
                MozcErrorCode.INITIALIZATION_FAILED
            )
        }
    }

    /**
     * Mozcデータファイルをアセットからコピーして初期化
     *
     * @param context Androidコンテキスト
     * @param assetPath アセットパス
     * @param config Mozc設定
     * @return MozcConverterインスタンス
     */
    fun createFromContextWithAsset(
        context: Context,
        assetPath: String,
        config: MozcConfig = MozcConfig.DEFAULT
    ): MozcResult<MozcConverter> {
        return try {
            val cacheFile = File(context.cacheDir, "mozc.data")
            if (!cacheFile.exists()) {
                context.assets.open(assetPath).use { inputStream ->
                    cacheFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            createFromContext(context, cacheFile, config)
        } catch (e: Exception) {
            MozcResult.Error(
                "Failed to load asset: ${e.message}",
                e,
                MozcErrorCode.INITIALIZATION_FAILED
            )
        }
    }

    /**
     * カスタムレイヤーでMozcConverterを作成（上級者向け）
     *
     * @param core カスタムCore実装
     * @param config Mozc設定
     * @return MozcConverterインスタンス
     */
    fun createWithCustomCore(
        core: MozcCore,
        config: MozcConfig = MozcConfig.DEFAULT
    ): MozcResult<MozcConverter> {
        return try {
            val session = MozcSessionImpl(core, config)
            val converter = MozcConverterImpl(session)
            MozcResult.Success(converter)
        } catch (e: Exception) {
            MozcResult.Error(
                "Failed to create MozcConverter: ${e.message}",
                e
            )
        }
    }

    /**
     * カスタムセッションでMozcConverterを作成（上級者向け）
     *
     * @param session カスタムSession実装
     * @return MozcConverterインスタンス
     */
    fun createWithCustomSession(
        session: MozcSession
    ): MozcResult<MozcConverter> {
        return try {
            val converter = MozcConverterImpl(session)
            MozcResult.Success(converter)
        } catch (e: Exception) {
            MozcResult.Error(
                "Failed to create MozcConverter: ${e.message}",
                e
            )
        }
    }
}
