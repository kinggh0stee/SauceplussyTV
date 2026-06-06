package com.saucedplussytv.androidtv.browse;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.g00fy2.versioncompare.Version;
import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import kotlin.Unit;
import com.saucedplussytv.androidtv.BuildConfig;
import com.saucedplussytv.androidtv.Constants;
import com.saucedplussytv.androidtv.R;
import com.saucedplussytv.androidtv.authenticate.AuthManager;
import com.saucedplussytv.androidtv.card.CardPresenter;
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient;
import com.saucedplussytv.androidtv.client.SocketClient;
import com.saucedplussytv.androidtv.client.SyncEvent;
import com.saucedplussytv.androidtv.client.UserSync;
import com.saucedplussytv.androidtv.creator.FloatplaneLiveStream;
import com.saucedplussytv.androidtv.detail.DetailsActivity;
import com.saucedplussytv.androidtv.ext.MapExtensionKt;
import com.saucedplussytv.androidtv.models.ChildImage;
import com.saucedplussytv.androidtv.models.Creator;
import com.saucedplussytv.androidtv.models.Delivery;
import com.saucedplussytv.androidtv.models.Thumbnail;
import com.saucedplussytv.androidtv.models.Video;
import com.saucedplussytv.androidtv.models.VideoInfo;
import com.saucedplussytv.androidtv.models.VideoProgress;
import com.saucedplussytv.androidtv.playback.PlaybackActivity;
import com.saucedplussytv.androidtv.subscription.Subscription;
import com.saucedplussytv.androidtv.post.VideoAttachments;
import com.saucedplussytv.androidtv.subscription.SubscriptionHeaderPresenter;

public class MainFragment extends BrowseSupportFragment {

    private static final String TAG = "MainFragment";
    public static boolean debug = BuildConfig.DEBUG;

    private SaucedplussyTVClient client;
    private final String version = BuildConfig.VERSION_NAME;

    private SocketClient socketClient;
    private Socket socket;
    private final Gson gson = new Gson();

    private List<Subscription> subscriptions = new ArrayList<>();
    private NavigableMap<Integer, Video> strms = new TreeMap<>();
    private HashMap<String, ArrayList<Video>> videos = new HashMap<>();
    private List<VideoProgress> videoProgress = new ArrayList<>();
    private int subCount;
    private final HashMap<String, Integer> creatorPages = new HashMap<>();

    private int rowSelected;
    private int colSelected;

    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private int liveIndex = -1;
    private boolean backgroundManagerPrepared = false;
    private boolean uiInitialized = false;
    private boolean isLoggedIn = false;
    private boolean adapterInitialized = false;
    private int loadGeneration = 0;
    private int subsRetryCount = 0;

    private ActivityResultLauncher<Intent> loginLauncher;
    private ActivityResultLauncher<Intent> detailLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String sessionCookie = data.getStringExtra(com.saucedplussytv.androidtv.authenticate.WebLoginActivity.EXTRA_SESSION_COOKIE);
                    String userAgent = data.getStringExtra(com.saucedplussytv.androidtv.authenticate.WebLoginActivity.EXTRA_USER_AGENT);
                    if (sessionCookie != null && !sessionCookie.isEmpty()) {
                        AuthManager authManager = AuthManager.Companion.getInstance(requireActivity());
                        authManager.saveSession(sessionCookie, userAgent != null ? userAgent : "");
                        isLoggedIn = true;
                        // Brief delay: cf_clearance needs ~1s to propagate across CF's CDN
                        // before the app's native HTTP client can use it successfully.
                        liveHandler.postDelayed(() -> {
                            if (isLoggedIn && isAdded()) initialize();
                        }, 1500);
                    } else {
                        dLog(TAG, "Login result missing session cookie; restarting login flow.");
                        checkLogin();
                    }
                }
            }
        );
        detailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    if (result.getData().getBooleanExtra("REFRESH", false)) {
                        refreshVideoProgress();
                    }
                }
            }
        );
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        client = SaucedplussyTVClient.Companion.getInstance(requireActivity());
        socketClient = SocketClient.Companion.getInstance(requireActivity());
        checkLogin();

        client.getLatest(v -> {
            if (!isAdded() || getContext() == null) return Unit.INSTANCE;
            if (v == null || v.length() < 2) return Unit.INSTANCE;
            if (new Version(version).isLowerThan(v.substring(1))) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Update Available");
                builder.setMessage("Version " + v + " now available via Github: \n\nhttps://github.com/kinggh0stee/Sauce-AndroidTV/releases");
                builder.setPositiveButton("OKAY", null);
                builder.create().show();
            }
            /*if (!version.equalsIgnoreCase(v.substring(1))) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Update Available");
                builder.setMessage("Version " + v + " now available via Github: \n\nhttps://github.com/kinggh0stee/Sauce-AndroidTV/releases");
                builder.setPositiveButton("OKAY", null);
                builder.create().show();
            }*/
            return Unit.INSTANCE;
        });
    }

    private void checkLogin() {
        AuthManager authManager = AuthManager.Companion.getInstance(requireActivity());
        authManager.withValidAccessToken(accessToken -> {
            dLog("LOGIN", "Access token valid (or refreshed successfully)");
            isLoggedIn = true;
            initialize();
            return Unit.INSTANCE;
        }, () -> {
            dLog("LOGIN", "No stored session; starting login flow.");
            isLoggedIn = false;
            Intent intent = new Intent(getActivity(), com.saucedplussytv.androidtv.authenticate.WebLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            loginLauncher.launch(intent);
            return Unit.INSTANCE;
        });
    }


    @Override
    public void onDestroyView() {
        liveHandler.removeCallbacksAndMessages(null);
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
        super.onDestroyView();
    }

    private void initialize() {
        subsRetryCount = 0;
        refreshSubscriptions();
        prepareBackgroundManager();
        
        // Only setup UI elements and listeners once, before views are created
        if (!uiInitialized) {
            setupUIElements();
            setupEventListeners();
            uiInitialized = true;
        }
        // TODO: Temporarily disabled - backend doesn't support auth tokens with websockets yet
        // Setup Socket
        // setupSocket();
    }

    private void setupSocket() {
        socketClient.initialize(sock -> {
            if (sock == null) {
                dLog("SOCKET", "Failed to initialize socket due to auth error");
                return Unit.INSTANCE;
            }

            socket = sock;
            socket.on("connect", onSocketConnect);
            socket.on("disconnect", onSocketDisconnect);
            socket.on("syncEvent", onSyncEvent);
            return Unit.INSTANCE;
        });
    }

    // Socket Event Emitters
    private final Emitter.Listener onSocketConnect = args -> {
        dLog("SOCKET", "Connected");
        JSONObject jo = new JSONObject();
        try {
            jo.put("url", "/api/v3/socket/connect");
            dLog("SOCKET --> EMIT", jo.toString());
            socket.emit("post", jo, new Ack() {
                @Override
                public void call(Object... args) {
                    UserSync us = socketClient.parseUserSync(args[0].toString());
                    dLog("SOCKET --> EMIT RESPONSE", String.valueOf(us));
                    if (us != null && us.getStatusCode() != null && us.getStatusCode() == 200) {
                        dLog("SOCKET", "Synced!");
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    };

    private final Emitter.Listener onSocketDisconnect = args -> {
        dLog("SOCKET", "Disconnected");
        // TODO: Temporarily disabled - backend doesn't support auth tokens with websockets yet
        // setupSocket();
    };

    private final Emitter.Listener onSyncEvent = args -> {
        JSONObject obj = (JSONObject) args[0];
        SyncEvent event = socketClient.parseSyncEvent(obj);
        if (event == null || event.getEvent() == null || event.getData() == null) return;
        String e = gson.toJson(event);
        dLog("SOCKET", e);
        if ("creatorNotification".equalsIgnoreCase(event.getEvent())) {
            dLog("SOCKET", "creatorNotification");
            String eventType = event.getData().getEventType();
            if (eventType == null) return;
            if (eventType.equalsIgnoreCase("CONTENT_LIVESTREAM_START")) {
                dLog("SOCKET", "CONTENT_LIVESTREAM_START");
                if (event.getData().getCreator() == null) return;
                Integer row = getRow(event.getData().getCreator(), subscriptions);
                Thumbnail th = new Thumbnail();
                th.setPath(event.getData().getIcon());
                if (strms.containsKey(row))
                    strms.get(row).setThumbnail(th);

                if (row != -1 && strms.containsKey(row)) {
                    android.app.Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> addToRow(strms.get(row)));
                    }
                }
            } else if (eventType.equalsIgnoreCase("CONTENT_POST_RELEASE")) {
                dLog("SOCKET", "CONTENT_POST_RELEASE");
                if (event.getData().getVideo() == null
                        || event.getData().getVideo().getGuid() == null) return;
                client.getVideoObject(event.getData().getVideo().getGuid(), video -> {
                    int row = getRow(video, subscriptions);
                    if (row != -1) {
                        android.app.Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> addToRow(video));
                        }
                    }
                    return Unit.INSTANCE;
                });
            }
        }
        dLog("SOCKET --> SYNCEVENT", event.toString());
    };

    /**
     * Full sign-out initiated by the user (Settings → Logout).
     * Clears WebView cookies so the next login forces a fresh account selection.
     */
    private void logout() {
        doLogout(true);
    }

    /**
     * Session-expiry relink — clears both the stored token AND the WebView cookie jar.
     * Sauce+ has no cf_clearance cookie (uses Private Access Tokens + _cfuvid instead),
     * so preserving WebView cookies gives no benefit and causes a probe-fires-too-early
     * loop: probeAuth() detects the stale __Host-sp-sess, acceptLogin() fires before the
     * user can enter credentials, and the expired session is reused — looping forever.
     */
    private void relinkSession() {
        doLogout(true);
    }

    private void doLogout(boolean clearWebCookies) {
        // Clear the stored Sauce+ session cookie / User-Agent.
        AuthManager authManager = AuthManager.Companion.getInstance(requireActivity());
        authManager.clearTokens();

        if (clearWebCookies) {
            // Full logout: wipe the WebView cookie jar so the next WebLoginActivity
            // session starts clean and can log in as a different account.
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            android.webkit.CookieManager.getInstance().flush();
        }

        // Clear all in-memory data structures
        subscriptions.clear();
        videos.clear();
        strms.clear();
        videoProgress.clear();
        creatorPages.clear();

        // Reset state variables
        subCount = 0;
        rowSelected = 0;
        colSelected = 0;
        liveIndex = -1;

        // Remove any pending live handler callbacks to prevent accessing cleared data
        liveHandler.removeCallbacksAndMessages(null);

        // Disconnect socket if connected
        if (socket != null && socket.connected()) {
            socket.disconnect();
            socket.off();
            socket = null;
        }

        // Clear the adapter to prevent stale data from being displayed
        if (getAdapter() != null) {
            setAdapter(null);
        }

        // Reset adapter initialization flag
        adapterInitialized = false;

        // Reset UI initialization flag to allow proper setup on next login
        uiInitialized = false;

        // Invalidate any in-flight getVideos callbacks from the previous session
        loadGeneration++;
        subsRetryCount = 0;

        // Mark as logged out to prevent callbacks from updating UI
        isLoggedIn = false;

        // Restart the web login flow instead of closing the app
        checkLogin();
    }

    private void gotLiveInfo(Subscription sub, Delivery live) {
        // Guard against processing live info if we're logged out
        if (!isLoggedIn) {
            dLog(TAG, "Ignoring live info update - user is logged out");
            return;
        }

        if (live.getGroups() == null || live.getGroups().isEmpty()
                || live.getGroups().get(0).getOrigins() == null || live.getGroups().get(0).getOrigins().isEmpty()
                || live.getGroups().get(0).getVariants() == null || live.getGroups().get(0).getVariants().isEmpty()) {
            dLog(TAG, "gotLiveInfo: incomplete delivery response, skipping");
            return;
        }
        String l = live.getGroups().get(0).getOrigins().get(0).getUrl() + live.getGroups().get(0).getVariants().get(0).getUrl();
        sub.setStreamUrl(l);
        client.checkLive(l, (status) -> {
            // Double-check we're still logged in when callback executes
            if (!isLoggedIn) {
                dLog(TAG, "Ignoring live status callback - user logged out during request");
                return Unit.INSTANCE;
            }
            sub.setStreaming(status == 200);
            dLog("LIVE STATUS", String.valueOf(status));
            return Unit.INSTANCE;
        });
        dLog("LIVE", l);
    }

    private void refreshSubscriptions() {
        final int gen = loadGeneration;
        client.getSubs(subscriptions -> {
            if (loadGeneration != gen) return Unit.INSTANCE;
            if (!isAdded() || getContext() == null) return Unit.INSTANCE;
            if (subscriptions == null) {
                // Retry a few times before showing the dialog — the session cookie can
                // take several seconds to propagate to the backend after a fresh login.
                if (subsRetryCount < 3) {
                    subsRetryCount++;
                    dLog(TAG, "getSubs returned null, retry " + subsRetryCount + "/3 in 3s");
                    liveHandler.postDelayed(() -> {
                        if (loadGeneration == gen && isLoggedIn && isAdded()) {
                            refreshSubscriptions();
                        }
                    }, 3000);
                    return Unit.INSTANCE;
                }
                subsRetryCount = 0;
                new AlertDialog.Builder(getContext())
                        .setTitle("Session Expired")
                        .setMessage("Your SaucedplussyTV session has expired. Please log in again.")
                        .setPositiveButton("Log in",
                                (dialog, which) -> {
                                    dialog.dismiss();
                                    relinkSession();
                                })
                        .setNegativeButton("Cancel",
                                (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            } else {
                subsRetryCount = 0;
                if (subscriptions.length == 0) {
                    new AlertDialog.Builder(getContext())
                            .setTitle("No Subscriptions Found")
                            .setMessage("Must be subscribed to a creator to utilize this app -- see official SaucedplussyTV website.")
                            .setPositiveButton("OK",
                                    (dialog, which) -> {
                                        dialog.dismiss();
                                        relinkSession();
                                    })
                            .create()
                            .show();
                    return Unit.INSTANCE;
                }
                gotSubscriptions(subscriptions);
            }

            return Unit.INSTANCE;
        });
    }

    private void gotSubscriptions(Subscription[] subs) {
        if (!isAdded() || getActivity() == null) return;
        // Guard against processing subscriptions if we're logged out
        if (!isLoggedIn) {
            dLog(TAG, "Ignoring subscription update - user is logged out");
            return;
        }
        
        List<Subscription> trimmed = new ArrayList<>();
        for (Subscription sub : subs) {
            if (trimmed.size() > 0) {
                if (!containsSub(trimmed, sub)) {
                    trimmed.add(sub);
                }
            } else {
                trimmed.add(sub);
            }
        }
        subscriptions = trimmed;
        final int gen = loadGeneration;
        int pending = 0;
        for (Subscription sub : subscriptions) {
            if (sub.getCreator() != null) {
                pending++;
                client.getLive(sub, live -> {
                    if (loadGeneration != gen) return Unit.INSTANCE;
                    gotLiveInfo(sub, live);
                    return Unit.INSTANCE;
                });
                client.getVideos(sub.getCreator(), 1, videos -> {
                    if (loadGeneration != gen) return Unit.INSTANCE;
                    gotVideos(sub.getCreator(), videos);
                    return Unit.INSTANCE;
                });
            }
        }
        subCount = pending;
        dLog("ROWS", trimmed.size() + "");
    }

    private boolean containsSub(List<Subscription> trimmed, Subscription sub) {
        for (Subscription s : trimmed) {
            if (s.getCreator() != null && s.getCreator().equals(sub.getCreator())) {
                return true;
            }
        }

        return false;
    }

    private void gotVideos(String creatorGUID, Video[] vids) {
        if (!isAdded() || getActivity() == null) return;
        if (!isLoggedIn) {
            dLog(TAG, "Ignoring video update - user is logged out");
            return;
        }
        if (vids == null) return;

        boolean isPagination = adapterInitialized && videos.get(creatorGUID) != null && videos.get(creatorGUID).size() > 0;
        int previousSize = (videos.get(creatorGUID) != null) ? videos.get(creatorGUID).size() : 0;
        
        if (videos.get(creatorGUID) != null && videos.get(creatorGUID).size() > 0) {
            videos.get(creatorGUID).addAll(Arrays.asList(vids));
        } else {
            videos.put(creatorGUID, new ArrayList<>(Arrays.asList(vids)));
        }

        if (isPagination) {
            appendVideosToRows();
        } else {
            // Initial load - wait for all subscriptions to finish, then do full refresh
            if (subCount > 1) {
                subCount--;
            } else {
                refreshVideoProgress();
                subCount = subscriptions.size();
            }
        }
    }

    private void refreshVideoProgress() {
        if (!isAdded() || getActivity() == null) return;
        // Guard against refreshing progress if we're logged out
        if (!isLoggedIn) {
            dLog(TAG, "Ignoring video progress refresh - user is logged out");
            return;
        }
        
        client.getVideoProgress(MapExtensionKt.getBlogPostIdsFromCreatorMap(videos), progress -> {
            // Double-check we're still logged in when callback executes
            if (!isLoggedIn) {
                dLog(TAG, "Ignoring video progress callback - user logged out during request");
                return Unit.INSTANCE;
            }
            videoProgress = progress;
            refreshRows();
            return Unit.INSTANCE;
        });
    }

    private void refreshRows() {
        if (!isAdded() || getActivity() == null) return;
        // Guard against refreshing rows if we're logged out
        if (!isLoggedIn) {
            dLog(TAG, "Ignoring row refresh - user is logged out");
            return;
        }
        
        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        CardPresenter cardPresenter = new CardPresenter(videoProgress);

        // Browse row: merge all fetched videos, sort by releaseDate descending, take first 20.
        // releaseDate is ISO-8601 so lexicographic comparison is equivalent to chronological.
        List<Video> allVideos = new ArrayList<>();
        for (List<Video> vids : videos.values()) {
            if (vids != null) allVideos.addAll(vids);
        }
        allVideos.sort((a, b) -> {
            String da = a.getReleaseDate() != null ? a.getReleaseDate() : "";
            String db = b.getReleaseDate() != null ? b.getReleaseDate() : "";
            return db.compareTo(da);
        });
        ArrayObjectAdapter browseAdapter = new ArrayObjectAdapter(cardPresenter);
        allVideos.subList(0, Math.min(20, allVideos.size())).forEach(browseAdapter::add);
        rowsAdapter.add(new ListRow(new HeaderItem(0, getString(R.string.browse)), browseAdapter));

        HeaderItem gridHeader = new HeaderItem(1, getString(R.string.settings));

        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        gridRowAdapter.add(getResources().getString(R.string.refresh));
        gridRowAdapter.add(getResources().getString(R.string.live_stream));
        gridRowAdapter.add(getResources().getString(R.string.app_info));
        gridRowAdapter.add(getResources().getString(R.string.logout));
        rowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        boolean isFirstBuild = !adapterInitialized;
        setAdapter(rowsAdapter);
        adapterInitialized = true;

        if (isFirstBuild) {
            // Post so the RecyclerView layout pass completes before we request focus on a card.
            // setSelectedPosition alone is enough — startHeadersTransition(false) is NOT called
            // because it hides the header panel entirely and its timing is unreliable after
            // setAdapter(), which left focus stuck at the row level with no left/right movement.
            liveHandler.post(() ->
                setSelectedPosition(rowSelected, false, new ListRowPresenter.SelectItemViewHolderTask(colSelected))
            );
        }
    }

    private void addLiveToRow(Video stream) {
        // Browse-only layout: all content goes into row 0 (Browse).
        addToBrowseRow(stream);
    }

    private void setupLiveCheck() {
        if (liveIndex == -1) {
            liveIndex = strms.firstKey();
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final Video stream = strms.get(liveIndex);

                if (stream != null) {
                    client.checkLive(stream.getVidUrl(), status -> {
                        if (status == 200) {
                            addLiveToRow(stream);
                            liveHandler.removeCallbacks(this);
                        } else {
                            liveHandler.postDelayed(this, 10000);
                        }
                        try {
                            liveIndex = strms.higherKey(liveIndex);
                        } catch (NullPointerException e) {
                            if (liveIndex == strms.lastKey()) {
                                liveIndex = strms.firstKey();
                            }
                        }

                        return Unit.INSTANCE;
                    });
                }
            }
        };
        liveHandler.post(runnable);
    }

    private void appendVideosToRows() {
        if (!isLoggedIn) return;
        ArrayObjectAdapter rowsAdapter = (ArrayObjectAdapter) getAdapter();
        if (rowsAdapter == null || rowsAdapter.size() == 0) {
            refreshVideoProgress();
            return;
        }
        // Browse row is always at index 0. Rebuild its content from the full merged+sorted pool.
        ListRow browseRow = (ListRow) rowsAdapter.get(0);
        if (browseRow == null) return;
        ArrayObjectAdapter browseAdapter = (ArrayObjectAdapter) browseRow.getAdapter();
        if (browseAdapter == null) return;

        List<Video> allVideos = new ArrayList<>();
        for (List<Video> vids : videos.values()) {
            if (vids != null) allVideos.addAll(vids);
        }
        allVideos.sort((a, b) -> {
            String da = a.getReleaseDate() != null ? a.getReleaseDate() : "";
            String db = b.getReleaseDate() != null ? b.getReleaseDate() : "";
            return db.compareTo(da);
        });
        List<Video> topVideos = allVideos.subList(0, Math.min(20, allVideos.size()));
        browseAdapter.clear();
        topVideos.forEach(browseAdapter::add);
    }

    private void addToRow(Video video) {
        // Browse-only layout: all content goes into row 0 (Browse).
        dLog("addToRow", video.getGuid());
        addToBrowseRow(video);
    }

    private void addToBrowseRow(Video video) {
        ArrayObjectAdapter rows = (ArrayObjectAdapter) getAdapter();
        if (rows == null || rows.size() == 0) return;
        ListRow browseRow = (ListRow) rows.get(0);
        if (browseRow == null) return;
        ArrayObjectAdapter vids = (ArrayObjectAdapter) browseRow.getAdapter();
        if (vids == null) return;
        for (int z = 0; z < vids.size(); z++) {
            if (((Video) vids.get(z)).getGuid().equalsIgnoreCase(video.getGuid())) return;
        }
        vids.add(0, video);
        vids.notifyArrayItemRangeChanged(0, vids.size());
    }

    private int getRow(Video video, List<Subscription> subs) {
        int row = -1;
        for (int i = 0; i < subs.size(); i++) {
            if (subs.get(i).getCreator().equalsIgnoreCase(video.getCreator().getId())) {
                row = i;
            }
        }
        return row;
    }

    private int getRow(String creatorGUID, List<Subscription> subs) {
        int row = -1;
        for (int i = 0; i < subs.size(); i++) {
            if (subs.get(i).getCreator().equalsIgnoreCase(creatorGUID)) {
                row = i;
            }
        }
        return row;
    }

    private void prepareBackgroundManager() {
        // BackgroundManager is a singleton per activity, so we need to avoid attaching multiple times
        if (backgroundManagerPrepared) {
            dLog(TAG, "BackgroundManager already prepared, skipping");
            return;
        }
        
        try {
            BackgroundManager mBackgroundManager = BackgroundManager.getInstance(requireActivity());
            mBackgroundManager.attach(requireActivity().getWindow());
            backgroundManagerPrepared = true;
        } catch (IllegalStateException e) {
            // BackgroundManager is already attached, which is fine
            dLog(TAG, "BackgroundManager already attached: " + e.getMessage());
            backgroundManagerPrepared = true;
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void setupUIElements() {
        setBadgeDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.icon));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object item) {
                return new SubscriptionHeaderPresenter();
            }
        });

        setBrandColor(ContextCompat.getColor(requireContext(), R.color.fastlane_background));
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new BrowseViewClickListener(requireContext(), this::onVideoSelected, this::onSettingsSelected));
        setOnItemViewSelectedListener(new ItemViewSelectedListener(this::onCheckIndices, this::onRowSelected));
    }

    private Unit onCheckIndices(@NonNull String creator, int selected) {
        colSelected = selected;

        subscriptions.forEach(sub -> {
            if (creator.equals(sub.getCreator())) {
                rowSelected = subscriptions.indexOf(sub);
            }
        });
        return Unit.INSTANCE;
    }

    private Unit onRowSelected() {
        // Fetch the next page only for the creator whose row just hit the last item.
        // (Previously fetched for all subscriptions and used a single page counter,
        // skipping pages when more than one subscription is loaded.)
        if (rowSelected < 0 || rowSelected >= subscriptions.size()) return Unit.INSTANCE;
        String creator = subscriptions.get(rowSelected).getCreator();
        if (creator == null) return Unit.INSTANCE;
        int nextPage = creatorPages.getOrDefault(creator, 1) + 1;
        final int gen = loadGeneration;
        client.getVideos(creator, nextPage, vids -> {
            if (loadGeneration != gen) return Unit.INSTANCE;
            // Only advance the page counter when the request succeeds with data,
            // so a network error doesn't permanently skip a page.
            if (vids != null && vids.length > 0) {
                creatorPages.put(creator, nextPage);
            }
            gotVideos(creator, vids);
            return Unit.INSTANCE;
        });
        return Unit.INSTANCE;
    }

    private Unit onVideoSelected(@Nullable Presenter.ViewHolder itemViewHolder, @NonNull Video video) {
        if (itemViewHolder != null) {
            // Get intent to switch to DetailActivity ready
            Intent intent = new Intent(getActivity(), DetailsActivity.class);

            // Setup transition animation to detail screen
            ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            requireActivity(),
                            itemViewHolder.view.findViewById(R.id.image),
                            DetailsActivity.SHARED_ELEMENT_NAME);

            if ("live".equalsIgnoreCase(video.getType())) {
                intent.putExtra(DetailsActivity.Video, video);
                requireActivity().startActivity(intent, activityOptions.toBundle());
            } else {
                // getVideoId() returns attachmentOrder[0] from the list response, which may be
                // absent or may contain a non-video attachment ID. getPost gives us the canonical
                // videoAttachments[0].guid that /api/v3/content/video and /api/v3/delivery/info
                // actually require — the same lookup VideoDetailsFragment.ACTION_RES already uses.
                client.getPost(video.getGuid(), post -> {
                    if (post == null) {
                        Toast.makeText(getActivity(), "Could not load video", Toast.LENGTH_SHORT).show();
                        return Unit.INSTANCE;
                    }
                    List<VideoAttachments> attachments = post.getVideoAttachments();
                    if (attachments == null || attachments.isEmpty()) {
                        Toast.makeText(getActivity(), "Could not load video", Toast.LENGTH_SHORT).show();
                        return Unit.INSTANCE;
                    }
                    String videoId = attachments.get(0).getGuid();
                    // Patch so getVideo's entityId param is also correct
                    video.setAttachmentIds(new String[]{ videoId });
                    client.getVideoInfo(videoId, videoInfo -> {
                        if (videoInfo == null) {
                            Toast.makeText(getActivity(), "Could not load video", Toast.LENGTH_SHORT).show();
                            return Unit.INSTANCE;
                        }
                        String res = getHighestSupportedRes(videoInfo);
                        client.getVideo(video, res, newVideo -> {
                            if (newVideo.getVidUrl() == null || newVideo.getVidUrl().isEmpty()) {
                                Toast.makeText(getActivity(), "Video URL unavailable", Toast.LENGTH_SHORT).show();
                                return Unit.INSTANCE;
                            }
                            newVideo.setVideoInfo(videoInfo);
                            intent.putExtra(DetailsActivity.Video, newVideo);
                            detailLauncher.launch(intent, activityOptions);
                            return Unit.INSTANCE;
                        });
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
            }
        }

        return Unit.INSTANCE;
    }

    private Unit onSettingsSelected(@NonNull SettingsAction action) {
        switch (action) {
            case REFRESH:
                loadGeneration++;
                subsRetryCount = 0;
                videos.clear();
                creatorPages.clear();
                adapterInitialized = false;
                rowSelected = 0;
                colSelected = 0;
                refreshSubscriptions();
                break;
            case LOGOUT:
                logout();
                break;
            case APP_INFO:
                showInfo();
                break;
            case LIVESTREAM:
                selectLivestream();
                break;
        }
        return Unit.INSTANCE;
    }

    private void showInfo() {
        new AlertDialog.Builder(getContext())
                .setTitle("SaucedplussyTV")
                .setMessage("Version: " + version + "\n\n" +
                        "Unofficial client for Sauce+\n\n" +
                        "Forked from Hydravion by:\n" +
                        "- bmlzootown\n" +
                        "- NickM-27\n" +
                        "- Jman012\n")
                //.setPositiveButton("OKAY", null)
                .create()
                .show();
    }

    private void selectLivestream() {
        List<String> subs = new ArrayList<>();
        for (Subscription s : subscriptions) {
            if (s.getPlan() != null) {
                subs.add(s.getPlan().getTitle());
            }
        }
        CharSequence[] s = subs.toArray(new CharSequence[0]);
        new AlertDialog.Builder(getContext())
                .setTitle("Play livestream?")
                .setItems(s, (dialog, which) -> {
                    String stream = subscriptions.get(which).getStreamUrl();
                    if (stream != null) {
                        dLog("LIVE", stream);
                        Video live = new Video();
                        live.setVidUrl(stream);
                        Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                        intent.putExtra(DetailsActivity.Video, live);
                        startActivity(intent);
                    } else {
                        Toast.makeText(getActivity(), "Subscription does not include access to livestream.", Toast.LENGTH_LONG).show();
                    }
                })
                .create()
                .show();
    }

    private String getHighestSupportedRes(VideoInfo info) {
        if (info == null || info.getLevels() == null || info.getLevels().isEmpty()) {
            dLog("Supported Resolution", "1080 (fallback - no level info)");
            return "1080";
        }
        int y = requireContext().getResources().getDisplayMetrics().heightPixels;
        AtomicBoolean found = new AtomicBoolean(false);
        String res = "";
        info.getLevels().forEach(level -> {
            if (level.getName().equalsIgnoreCase(Integer.toString(y))) {
                found.set(true);
            }
        });
        if (found.get()) {
            res = Integer.toString(y);
        } else {
            res = "1080";
        }

        dLog("Supported Resolution", res);
        return res;
    }

    public static void dLog(String tag, String msg) {
        if (debug) {
            Log.d(tag, msg);
        }
    }

    public static void dError(String tag, String msg) {
        if (debug) {
            Log.e(tag, msg);
        }
    }
}