package com.alpha.spendtracker.di

import com.alpha.spendtracker.data.FirebaseLlmEvents
import com.alpha.spendtracker.data.GeminiGateway
import com.alpha.spendtracker.data.GeminiNanoGateway
import com.alpha.spendtracker.data.LlmConfig
import com.alpha.spendtracker.data.LlmEvents
import com.alpha.spendtracker.data.LocalLlmGateway
import com.alpha.spendtracker.data.RemoteLlmConfig
import com.alpha.spendtracker.data.SdkGeminiGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wires [com.alpha.spendtracker.data.LlmClient]'s collaborators to their Android implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmConfig(impl: RemoteLlmConfig): LlmConfig

    @Binds
    @Singleton
    abstract fun bindGeminiGateway(impl: SdkGeminiGateway): GeminiGateway

    @Binds
    @Singleton
    abstract fun bindLlmEvents(impl: FirebaseLlmEvents): LlmEvents

    @Binds
    @Singleton
    abstract fun bindLocalLlm(impl: GeminiNanoGateway): LocalLlmGateway
}
