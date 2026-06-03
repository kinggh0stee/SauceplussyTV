package com.saucedplussytv.androidtv.card

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.parseAsHtml
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.leanback.widget.Presenter
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.saucedplussytv.androidtv.R
import com.saucedplussytv.androidtv.ext.getTagColor
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoProgress

class CardPresenter(private val videoProgress: List<VideoProgress>) : Presenter() {

    /** Custom ViewHolder that keeps a stable CardViewHolder reference for onViewFocused. */
    private class CardPresenterViewHolder(view: View, val card: CardViewHolder) : ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val inflated = LayoutInflater.from(parent.context).inflate(R.layout.card_video, parent, false)
        val card = CardViewHolder(inflated)
        return CardPresenterViewHolder(inflated, card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        (item as? Video?)?.let { video ->
            (viewHolder as CardPresenterViewHolder).card
                .setData(video, videoProgress.find { it.id == video.id })
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder as CardPresenterViewHolder).card.unBind()
    }

    private class CardViewHolder(val rootView: View) {

        val version = com.saucedplussytv.androidtv.BuildConfig.VERSION_NAME

        /**
         * Views
         */
        private val image: ImageView = rootView.findViewById(R.id.image)
        private val progress: LinearProgressIndicator = rootView.findViewById(R.id.watch_progress)
        private val duration: TextView = rootView.findViewById(R.id.duration)
        private val title: TextView = rootView.findViewById(R.id.title)
        private val desc: TextView = rootView.findViewById(R.id.desc)
        private val tagList: LinearLayout = rootView.findViewById(R.id.tags)

        /**
         * Colors and objects that need context
         */
        private var defaultPrefs: SharedPreferences
        private var selectedBackgroundColor: Int = -1
        private var defaultBackgroundColor: Int = -1
        private var defaultCardImage: Drawable? = null

        init {
            rootView.context.run {
                defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this)
                defaultBackgroundColor = ContextCompat.getColor(this, R.color.default_background)
                selectedBackgroundColor = ContextCompat.getColor(this, R.color.selected_background)

                /**
                 * This template uses a default image in res/drawable, but the general case for Android TV
                 * will require your resources in xhdpi. For more information, see
                 * https://developer.android.com/training/tv/start/layouts.html#density-resources
                 */
                defaultCardImage = ContextCompat.getDrawable(this, R.drawable.white_plane)
            }
            // Leanback 1.0.0 does not expose Presenter.onViewFocused; Android system focus events
            // on the focusable root view are the correct mechanism for HorizontalGridView items.
            rootView.onFocusChangeListener =
                View.OnFocusChangeListener { _, isFocused -> setIsSelected(isFocused) }
            setIsSelected(false)
        }

        fun setIsSelected(isSelected: Boolean) {
            rootView.isSelected = isSelected
            title.isSelected = isSelected

            if (isSelected) {
                rootView.setBackgroundColor(selectedBackgroundColor)
                rootView.animate().scaleX(1.10f).scaleY(1.10f).setDuration(150).start()
                rootView.elevation = rootView.context.resources.displayMetrics.density * 8f
                title.ellipsize = TextUtils.TruncateAt.MARQUEE
                title.marqueeRepeatLimit = -1
                // Do NOT set isFocusable=true on the title — it would steal focus from the card root.
                // isSelected=true on a TextView is enough to trigger the marquee scroll animation.
                title.setHorizontallyScrolling(true)
            } else {
                rootView.setBackgroundColor(defaultBackgroundColor)
                rootView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                rootView.elevation = 0f
                title.ellipsize = TextUtils.TruncateAt.END
            }
        }

        fun setData(video: Video, videoProgress: VideoProgress?) {
            if (video.thumbnail != null && video.thumbnail!!.childImages != null) {
                title.text = video.title

                video.description.parseAsHtml().let { videoDesc ->
                    desc.text = videoDesc

                    if (videoDesc.isBlank()) {
                        desc.isInvisible = true
                        title.textSize = 20f
                    } else {
                        desc.isVisible = true
                        title.textSize = 16f
                    }
                }

                (if ((video.thumbnail?.childImages?.size ?: 0) > 0) {
                    video.thumbnail?.childImages?.get(0)?.path
                } else {
                    video.thumbnail?.path
                })?.let { thumbnail ->
                    Glide.with(rootView.context)
                        //.load(thumbnail)
                        .load(
                            GlideUrl(
                                thumbnail, LazyHeaders.Builder()
                                    .addHeader("User-Agent", "SaucedplussyTV (AndroidTV $version)")
                                    .build()
                            )
                        )
                        .centerCrop()
                        .error(defaultCardImage)
                        .into(image)
                }

                video.metadata?.videoDurationInSecs?.let { totalDurationSecs ->
                    if (totalDurationSecs <= 0) {
                        duration.isGone = true
                    } else {
                        duration.isVisible = true
                        duration.text = formatDuration(totalDurationSecs)
                    }
                } ?: run { duration.isGone = true }

                if (videoProgress != null) {
                    progress.isVisible = true
                    progress.min = 0
                    progress.max = 100 // to get in milliseconds like watch time
                    progress.progress = videoProgress.progress
                } else {
                    progress.isGone = true
                }

                if (video.tags.isNotEmpty()) {
                    tagList.removeAllViews()
                    tagList.visibility = View.VISIBLE
                    desc.maxLines = 1

                    video.tags.forEach { tag ->
                        (LayoutInflater.from(rootView.context)
                            .inflate(R.layout.view_tag, tagList, false) as TextView).apply {
                            text = "#$tag"
                            backgroundTintList = ColorStateList.valueOf(rootView.context.getTagColor(tag))
                            tagList.addView(this)
                        }
                    }
                } else {
                    tagList.removeAllViews()
                    tagList.visibility = View.GONE
                    desc.maxLines = 2
                }
            }
        }

        fun formatDuration(durationSecs: Int): String =
            DateUtils.formatElapsedTime(durationSecs.toLong())

        fun unBind() {
            image.setImageDrawable(null)
        }
    }
}