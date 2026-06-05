package com.saucedplussytv.androidtv.models

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
class Creator : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    @SerializedName("id")
    @Expose
    var id: String = ""

    @SerializedName("title")
    @Expose
    var title: String = ""
}