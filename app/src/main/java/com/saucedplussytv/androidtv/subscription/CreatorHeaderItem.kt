package com.saucedplussytv.androidtv.subscription

import androidx.leanback.widget.HeaderItem

/** HeaderItem that carries the creator GUID so the header presenter can do GUID-keyed icon lookup. */
class CreatorHeaderItem(id: Long, name: String, val creatorGUID: String) : HeaderItem(id, name)
