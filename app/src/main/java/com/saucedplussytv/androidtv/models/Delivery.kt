package com.saucedplussytv.androidtv.models

// Gson populates these via reflection and will happily leave a field null when the API
// omits it, regardless of the declared Kotlin type. Declaring the collections nullable
// makes the type match reality and keeps the callers' null checks meaningful.
data class Delivery (
    val groups: List<Group>?
)

data class Group (
    val origins: List<Origin>?,
    val variants: List<Variant>?
)

data class Origin (
    val url: String,
)

data class Variant (
    val name: String,
    val label: String,
    val url: String,
    val mimeType: String,
    val order: Int?,
    val hidden: Boolean,
    val enabled: Boolean,
)
