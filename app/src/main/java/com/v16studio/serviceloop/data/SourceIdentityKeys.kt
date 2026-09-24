package com.v16studio.serviceloop.data

internal object SourceIdentityKeys {
    fun evidence(origin: String, photo: String, revision: String?): String =
        "v1:" + listOf(origin, photo, revision).joinToString("") { value ->
            if (value == null) "-1:" else "${value.length}:$value"
        }
}
