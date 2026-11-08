package com.linroid.ketch.app.state

import com.linroid.ketch.config.AiSettings
import com.linroid.ketch.config.LlmSettings

class EmbeddedAiDiscoveryProviderFactory(
  @Suppress("UNUSED_PARAMETER") private val getenv: (String) -> String? = System::getenv,
) : AiDiscoveryProviderFactory {
  override fun create(settings: AiSettings): AiDiscoveryProvider? = null
  override fun withPlatformCredentials(settings: AiSettings): AiSettings = settings
  override suspend fun listModels(llm: LlmSettings): List<String> = emptyList()
}
