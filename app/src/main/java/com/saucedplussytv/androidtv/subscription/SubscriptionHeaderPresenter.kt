package com.saucedplussytv.androidtv.subscription

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions
import com.saucedplussytv.androidtv.R
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface SubscriptionClientEntryPoint {
    fun client(): SaucedplussyTVClient
}

class SubscriptionHeaderPresenter : RowHeaderPresenter() {

    private var client: SaucedplussyTVClient? = null
    val version = com.saucedplussytv.androidtv.BuildConfig.VERSION_NAME

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        if (client == null) {
            client = dagger.hilt.android.EntryPointAccessors.fromApplication(
                parent.context.applicationContext,
                SubscriptionClientEntryPoint::class.java
            ).client()
        }

        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.header_subscription, parent, false))
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val row = item as? Row ?: return
        val name = row.headerItem?.name ?: return
        val subView = viewHolder.view ?: return
        subView.findViewById<TextView>(R.id.header_sub).text = name
        val iconView = subView.findViewById<ImageView>(R.id.header_icon)

        when {
            name == subView.context.getString(R.string.settings) -> {
                iconView.setImageResource(R.drawable.ic_settings)
            }
            name == subView.context.getString(R.string.browse) -> {
                iconView.setImageDrawable(null)
            }
            else -> {
                // Look up by GUID (from CreatorHeaderItem) to avoid name-collision and
                // to handle async cache fills — getCreatorById fires the callback once the
                // icon is available, whether or not the cache was already warm.
                val guid = (row.headerItem as? CreatorHeaderItem)?.creatorGUID ?: return
                iconView.setImageDrawable(null)
                client?.getCreatorById(guid) { creator ->
                    val logoPath = creator.icon?.path
                    if (!logoPath.isNullOrEmpty() && subView.isAttachedToWindow) {
                        Glide.with(subView)
                            .load(
                                GlideUrl(
                                    logoPath, LazyHeaders.Builder()
                                        .addHeader("User-Agent", "SaucedplussyTV (AndroidTV $version)")
                                        .build()
                                )
                            )
                            .apply(RequestOptions.circleCropTransform())
                            .into(iconView)
                    }
                }
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) = Unit
}