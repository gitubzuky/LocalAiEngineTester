package com.zure.localaiengine.engines.llama

internal fun interface LlamaTokenCallback {
    fun onToken(token: String): Boolean
}
