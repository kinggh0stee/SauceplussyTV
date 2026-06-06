package com.saucedplussytv.androidtv.client

import android.annotation.SuppressLint
import android.content.Context
import com.android.volley.VolleyError
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.saucedplussytv.androidtv.BuildConfig
import com.saucedplussytv.androidtv.Constants
import com.saucedplussytv.androidtv.browse.MainFragment
import com.saucedplussytv.androidtv.authenticate.AuthManager
import com.saucedplussytv.androidtv.creator.Creator
import com.saucedplussytv.androidtv.creator.FloatplaneLiveStream
import com.saucedplussytv.androidtv.github.Release
import com.saucedplussytv.androidtv.models.*
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.post.Post
import com.saucedplussytv.androidtv.subscription.Subscription
import org.json.JSONArray
import org.json.JSONObject

class SaucedplussyTVClient private constructor(private val context: Context) {

    private val creatorCache: MutableMap<String, Creator> = hashMapOf()
    private val requestTask: RequestTask = RequestTask(context)
    private val authManager: AuthManager = AuthManager.getInstance(context)
    private val gson = Gson()

    fun getSubs(callback: (Array<Subscription>?) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(URI_SUBSCRIPTIONS, token, object : RequestTask.VolleyCallback {

            override fun onResponseCode(response: Int) {
                //Ignore
            }

            override fun onSuccess(response: String) {
                if (BuildConfig.DEBUG) {
                    MainFragment.dLog(TAG, "getSubs: $response")
                }

                if (response.contains("errors")) {
                    callback(null)
                    return
                }

                gson.fromJson(response, Array<Subscription>::class.java).let { subs ->
                    callback(subs)
                }
            }

            override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

            override fun onError(error: VolleyError) {
                MainFragment.dError(TAG, "getSubs error: ${error.javaClass.simpleName} status=${error.networkResponse?.statusCode}")
                callback(null)
            }
        })
        }, {
            callback(null)
        })
    }

    fun getCreatorInfo(creatorGUID: String, callback: (FloatplaneLiveStream) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_CREATOR_INFO?id=$creatorGUID",
                token,
                object : RequestTask.VolleyCallback {
                override fun onSuccess(response: String) {
                    if (BuildConfig.DEBUG) {
                        MainFragment.dLog(TAG,"getCreatorInfo: $response")
                    }

                    try {
                        parseCreator(response)?.lastLiveStream?.let { callback.invoke(it) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            // no-op on failure
        })
    }

    fun getVideos(creatorGUID: String, page: Int, callback: (Array<Video>) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_VIDEOS?id=$creatorGUID&fetchAfter=${(page - 1) * 20}",
                token,
                creatorGUID,
                object : RequestTask.VolleyCallback {

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccess(response: String) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) {
                    if (BuildConfig.DEBUG) {
                        MainFragment.dLog(TAG, "getVideos: $response")
                    }

                    callback(gson.fromJson(response, Array<Video>::class.java))
                }

                override fun onError(error: VolleyError) = callback(emptyArray())
            })
        }, {
            callback(emptyArray())
        })
    }

    fun getVideo(video: Video, res: String, callback: (Video) -> Unit) {
        //val y = Util.getCurrentDisplayModeSize(context).y;
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_DELIVERY?scenario=onDemand&entityId=${video.getVideoId()}",
                token,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    if (BuildConfig.DEBUG) {
                        MainFragment.dLog(TAG, "getVideo: $response")
                    }

                    val delivery = gson.fromJson(response, Delivery::class.java)
                    val groups = delivery?.groups
                    if (groups.isNullOrEmpty() || groups[0].origins.isNullOrEmpty()) {
                        MainFragment.dLog(TAG, "getVideo: no delivery groups or origins")
                        callback(video)
                        return
                    }
                    val cdn = groups[0].origins[0].url
                    val maxRes = res.toIntOrNull() ?: Int.MAX_VALUE
                    val variants = (groups[0].variants ?: emptyList())
                        .filter { v ->
                            val vRes = v.name?.substringBefore("-")?.toIntOrNull() ?: 0
                            vRes <= maxRes
                        }
                        .sortedWith(
                            compareByDescending<Variant> {
                                when (it.name) {
                                    "2160-avc1" -> 5
                                    "1080-avc1" -> 4
                                    "720-avc1" -> 3
                                    "480-avc1" -> 2
                                    "360-avc1" -> 1
                                    else -> 0
                                }
                            }
                        )

                    var uri = ""
                    for (variant in variants) {
                        MainFragment.dLog("VARIANT", variant.toString())
                        if (!variant.enabled) {
                            MainFragment.dLog("VARIANT", variant.label + " DISABLED")
                            continue
                        } else {
                            uri = variant.url
                            break
                        }
                    }
                    if (uri.isEmpty()) {
                        MainFragment.dLog(TAG, "getVideo: no enabled variant found")
                        callback(video)
                        return
                    }
                    video.vidUrl = cdn + uri
                    MainFragment.dLog(TAG, "Video: $video")
                    callback(video)
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = callback(video)
            })
        }, {
            callback(video)
        })
    }


    fun getVideoObject(id: String, callback: (Video) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_POST?id=$id",
                token,
                object : RequestTask.VolleyCallback {
                override fun onSuccess(response: String) {
                    try {
                        callback(gson.fromJson(response, Video::class.java))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            // no-op
        })
    }

    fun getVideoInfo(videoID: String, callback: (VideoInfo?) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_VIDEO_INFO?id=$videoID",
                token,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    try {
                        callback(gson.fromJson(response, VideoInfo::class.java))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(null)
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = callback(null)
            })
        }, {
            callback(null)
        })
    }

    fun getLive(sub: Subscription, callback: (Delivery) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_CREATOR?id=${sub.creator}",
                token,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    parseCreator(response)?.lastLiveStream?.let { liveStream ->
                        getLive(liveStream.id) { callback(it) }
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            // no-op
        })
    }

    fun getLive(livestreamID: String, callback: (Delivery) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_DELIVERY?scenario=live&entityId=$livestreamID",
                token,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(gson.fromJson(response, Delivery::class.java))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            // no-op
        })
    }

    /*fun getLive(creatorGUID: String, callback: (Live) -> Unit) {
        requestTask.sendRequest(
            "$URI_LIVE?type=live&creator=$creatorGUID",
            getCookiesString(),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(Gson().fromJson(response, Live::class.java))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }*/

    fun checkLive(streamUri: String, callback: (Int) -> Unit) {
        requestTask.getReponseStatus(streamUri, object : RequestTask.VolleyCallback {
            override fun onResponseCode(response: Int) {
                callback(response)
            }

            override fun onSuccess(response: String) = Unit

            override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

            override fun onError(error: VolleyError) = Unit

        })
    }

    fun getCreatorByName(name: String, callback: (Creator) -> Unit) {
        callback(creatorCache.values.firstOrNull { it.name == name } ?: Creator())
    }

    fun getCreatorTitle(id: String): String = creatorCache[id]?.name ?: ""

    fun getCreatorById(id: String, callback: (Creator) -> Unit) {
        if (id.isNotEmpty()) {
            // Check for existing logo, otherwise fetch it and then run the callback
            creatorCache[id]?.let { callback(it) } ?: run {
                cacheLogo(id, callback)
            }
        } else {
            callback(Creator())
        }
    }

    private fun cacheLogo(creatorGUID: String, callback: ((Creator) -> Unit)?) {
        if (creatorCache[creatorGUID] != null) {
            // If the logo already is cached, no reason to retrieve it again
            return
        }

        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_CREATOR_INFO?id=$creatorGUID",
                token,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    try {
                        val creator = parseCreator(response)
                        if (creator != null) {
                            creatorCache[creatorGUID] = creator
                            callback?.invoke(creator)
                        } else {
                            callback?.invoke(Creator())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback?.invoke(Creator())
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) {
                    callback?.invoke(Creator())
                }
            })
        }, {
            callback?.invoke(Creator())
        })
    }

    /** Parses a Creator from a response that may be a single object or a one-element array. */
    private fun parseCreator(response: String): Creator? = try {
        if (response.trimStart().startsWith("["))
            gson.fromJson(response, Array<Creator>::class.java).firstOrNull()
        else
            gson.fromJson(response, Creator::class.java)
    } catch (e: Exception) {
        null
    }

    fun getPost(postId: String, callback: (Post?) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_POST?id=$postId",
                token,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    try {
                        callback(gson.fromJson(response, Post::class.java))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(null)
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = callback(null)
            })
        }, {
            callback(null)
        })
    }

    fun getLatest(callback: (String) -> Unit) {
        requestTask.sendRequest(LATEST, "", object : RequestTask.VolleyCallback {
            override fun onResponseCode(response: Int) = Unit

            override fun onSuccess(response: String) {

                try {
                    callback(gson.fromJson(response, Release::class.java).tag_name)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

            override fun onError(error: VolleyError) = Unit

        });
    }

    fun toggleLikePost(postId: String, callback: (Boolean) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendData(
                URI_LIKE,
                token,
                mapOf("id" to postId, "contentType" to "blogPost"),
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(response.contains("like"))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            callback(false)
        })
    }

    fun toggleDislikePost(postId: String, callback: (Boolean) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendData(
                URI_DISLIKE,
                token,
                mapOf("id" to postId, "contentType" to "blogPost"),
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(response.contains("dislike"))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            callback(false)
        })
    }

    fun getVideoProgress(blogPostIds: List<String>, callback: (List<VideoProgress>) -> Unit) {

        val body = JSONObject().let { json ->
            json.put("ids", JSONArray(blogPostIds))
            json.put("contentType", "blogPost")
            json
        }.toString()
        authManager.withValidAccessToken({ token ->
            requestTask.sendDataWithBody(
                URI_GET_PROGRESS,
                token,
                body,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    try {
                        val type = (object : TypeToken<List<VideoProgress>>() {}).getType()
                        callback(Gson().fromJson(response, type))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(ArrayList())
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) {
                    callback(ArrayList())
                }
            })
        }, {
            callback(ArrayList())
        })
    }

    fun setVideoProgress(videoId: String, progressInPercent: Int) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendData(
                URI_UPDATE_PROGRESS,
                token,
                mapOf("id" to videoId, "contentType" to "video", "progress" to progressInPercent.toString()),
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) = Unit

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            // ignore
        })
    }

    companion object {

        private const val TAG = "SaucedplussyTVClient"
        // Sauce+ is a white-label Floatplane instance; the /api/v3 contract is identical,
        // only the host changes. See CLAUDE.md "Auth model" / reference/RECON.md.
        private const val SITE = "https://www.sauceplus.com"

        // Updated to v3 API
        private const val URI_SUBSCRIPTIONS = "$SITE/api/v3/user/subscriptions"
        private const val URI_CREATOR_INFO = "$SITE/api/v3/creator/info"

        // Already updated!
        private const val URI_DELIVERY = "$SITE/api/v3/delivery/info"
        private const val URI_CREATOR = "$SITE/api/v3/creator/info"
        private const val URI_VIDEOS = "$SITE/api/v3/content/creator"
        private const val URI_VIDEO_OBJECT = "$SITE/api/v3/content/info"
        private const val URI_VIDEO_INFO = "$SITE/api/v3/content/video"
        private const val URI_POST = "$SITE/api/v3/content/post"
        private const val URI_LIKE = "$SITE/api/v3/content/like"
        private const val URI_DISLIKE = "$SITE/api/v3/content/dislike"
        private const val URI_GET_PROGRESS = "$SITE/api/v3/content/get/progress"
        private const val URI_UPDATE_PROGRESS = "$SITE/api/v3/content/progress"

        private const val LATEST = "https://api.github.com/repos/kinggh0stee/Sauce-AndroidTV/releases/latest"
        @SuppressLint("StaticFieldLeak") // applicationContext only — no leak
        private var INSTANCE: SaucedplussyTVClient? = null

        @Synchronized
        fun getInstance(context: Context): SaucedplussyTVClient {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = SaucedplussyTVClient(context.applicationContext)
                }
            }

            return INSTANCE!!
        }
    }
}