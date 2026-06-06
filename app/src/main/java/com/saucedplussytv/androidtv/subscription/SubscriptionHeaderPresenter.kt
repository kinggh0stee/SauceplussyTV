package com.saucedplussytv.androidtv.subscription

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.RowHeaderPresenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions
import com.saucedplussytv.androidtv.R
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient

class SubscriptionHeaderPresenter : RowHeaderPresenter() {

    private var client: SaucedplussyTVClient? = null
    val version = com.saucedplussytv.androidtv.BuildConfig.VERSION_NAME

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        if (client == null) {
            client = SaucedplussyTVClient.getInstance(parent.context)
        }

        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.header_subscription, parent, false))
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        (item as? ListRow)?.headerItem?.name?.let { name ->
            viewHolder.view?.let { subView ->
                subView.findViewById<TextView>(R.id.header_sub).text = name
                when {
                    name == subView.context.getString(R.string.settings) -> {
                        subView.findViewById<ImageView>(R.id.header_icon).setImageResource(R.drawable.ic_settings)
                    }
                    name == subView.context.getString(R.string.browse) -> {
                        subView.findViewById<ImageView>(R.id.header_icon).setImageDrawable(null)
                    }
                    else -> {
                        client?.getCreatorByName(name) { creator ->
                            val logoPath = creator.icon?.path
                            if (!logoPath.isNullOrEmpty()) {
                                Glide.with(subView)
                                    .load(
                                        GlideUrl(
                                            logoPath, LazyHeaders.Builder()
                                                .addHeader("User-Agent", "SaucedplussyTV (AndroidTV $version)")
                                                .build()
                                        )
                                    )
                                    .apply(RequestOptions.circleCropTransform())
                                    .into(subView.findViewById(R.id.header_icon))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) = Unit
}