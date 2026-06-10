package com.saucedplussytv.androidtv.browse;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
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
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import kotlinx.coroutines.Job;
import com.saucedplussytv.androidtv.card.CardPresenter;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;
import io.github.g00fy2.versioncompare.Version;
import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import kotlin.Unit;
import com.saucedplussytv.androidtv.BuildConfig;
import com.saucedplussytv.androidtv.R;
import com.saucedplussytv.androidtv.authenticate.AuthManager;
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient;
import com.saucedplussytv.androidtv.client.SocketClient;
import com.saucedplussytv.androidtv.client.SyncEvent;
import com.saucedplussytv.androidtv.client.UserSync;
import com.saucedplussytv.androidtv.detail.DetailsActivity;
import com.saucedplussytv.androidtv.models.Thumbnail;
import com.saucedplussytv.androidtv.models.Video;
import com.saucedplussytv.androidtv.models.VideoInfo;
import com.saucedplussytv.androidtv.models.VideoProgress;
import com.saucedplussytv.androidtv.playback.PlaybackActivity;
import com.saucedplussytv.androidtv.subscription.CreatorHeaderItem;
import com.saucedplussytv.androidtv.subscription.Subscription;
import com.saucedplussytv.androidtv.post.VideoAttachments;
import com.saucedplussytv.androidtv.subscription.SubscriptionHeaderPresenter;

@AndroidEntryPoint
public class MainFragment extends BrowseSupportFragment {

    private static final String TAG = "MainFragment";
    public static boolean debug = BuildConfig.DEBUG;

    @Inject
    SaucedplussyTVClient client;
    @Inject
    SocketClient socketClient;
    @Inject
    AuthManager authManager;

    private final String version = BuildConfig.VERSION_NAME;
    private Socket socket;
    private final Gson gson = new Gson();

    private int rowSelected;
    private int colSelected;

    private Job liveCheckJob;
    private boolean backgroundManagerPrepared = false;
    private boolean uiInitialized = false;
    private boolean isLoggedIn = false;

    private BrowseGridFragment gridFragment;
    private List<Video> pendingBrowseVideos;
    private final HashMap<String, ArrayObjectAdapter> subRowAdapters = new HashMap<>();
    private ArrayObjectAdapter rowsAdapter;
    private long nextRowId = 1;
    private int settingsRowIndex = -1;

    private MainViewModel mainViewModel;

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
                        authManager.saveSession(sessionCookie, userAgent != null ? userAgent : "");
                        isLoggedIn = true;
                        // Brief delay: cf_clearance needs ~1s to propagate across CF's CDN
                        // before the app's native HTTP client can use it successfully.
                        MainFragmentExtKt.launchDelayed(getViewLifecycleOwner(), 1500L, () -> {
                            if (isLoggedIn && isAdded()) initialize();
                        });
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
                        if (isLoggedIn) mainViewModel.refreshVideoProgress();
                    }
                }
            }
        );
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        mainViewModel.getSessionExpired().observe(getViewLifecycleOwner(), event -> {
            if (event.getContentIfNotHandled() == null) return;
            Activity a = getActivity();
            if (a == null || !isAdded()) return;
            new AlertDialog.Builder(a)
                    .setTitle("Session Expired")
                    .setMessage("Your SaucedplussyTV session has expired. Please log in again.")
                    .setPositiveButton("Log in", (dialog, which) -> {
                        dialog.dismiss();
                        relinkSession();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .create()
                    .show();
        });

        mainViewModel.getNoSubscriptions().observe(getViewLifecycleOwner(), event -> {
            if (event.getContentIfNotHandled() == null) return;
            Activity a = getActivity();
            if (a == null || !isAdded()) return;
            new AlertDialog.Builder(a)
                    .setTitle("No Subscriptions Found")
                    .setMessage("Must be subscribed to a creator to utilize this app -- see official SaucedplussyTV website.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        dialog.dismiss();
                        relinkSession();
                    })
                    .create()
                    .show();
        });

        mainViewModel.getCreatorVideosUpdated().observe(getViewLifecycleOwner(), event -> {
            CreatorVideos cv = event.getContentIfNotHandled();
            if (cv == null) return;
            Activity a = getActivity();
            if (a == null || !isAdded() || !isLoggedIn) return;
            if (cv.isPagination()) {
                appendVideosToRows(cv.getCreatorGUID());
            } else if (rowsAdapter == null) {
                // Rows not yet built — initialize from the data accumulated so far
                initRows();
            } else {
                // Rows exist — incremental update for this creator
                List<Video> allVideos = mainViewModel.getMergedVideos();
                if (gridFragment != null) {
                    gridFragment.replaceVideos(allVideos, mainViewModel.getVideoProgress());
                } else {
                    pendingBrowseVideos = allVideos;
                }
                addOrUpdateSubRow(cv.getCreatorGUID());
            }
        });

        mainViewModel.getVideoProgressUpdated().observe(getViewLifecycleOwner(), ignored -> {
            BrowseGridFragment frag = gridFragment;
            if (frag != null && isAdded() && isLoggedIn) {
                frag.updateProgress(mainViewModel.getVideoProgress());
            }
        });

        getMainFragmentRegistry().registerFragment(PageRow.class, new BrowseSupportFragment.FragmentFactory<BrowseGridFragment>() {
            @Override
            public BrowseGridFragment createFragment(Object rowObj) {
                BrowseGridFragment frag = new BrowseGridFragment();
                gridFragment = frag;
                frag.setNearEndListener(MainFragment.this::onGridNearEnd);
                frag.setOnItemViewClickedListener(new BrowseViewClickListener(requireContext(),
                    MainFragment.this::onVideoSelected, MainFragment.this::onSettingsSelected));
                if (pendingBrowseVideos != null) {
                    frag.pendingVideos = pendingBrowseVideos;
                    frag.pendingProgress = mainViewModel.getVideoProgress();
                    pendingBrowseVideos = null;
                }
                return frag;
            }
        });

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
        authManager.withValidAccessToken(accessToken -> {
            dLog("LOGIN", "Access token valid (or refreshed successfully)");
            isLoggedIn = true;
            initialize();
            return Unit.INSTANCE;
        }, () -> {
            dLog("LOGIN", "No stored session; starting login flow.");
            isLoggedIn = false;
            Activity activity = getActivity();
            if (activity == null || !isAdded()) return Unit.INSTANCE;
            Intent intent = new Intent(activity, com.saucedplussytv.androidtv.authenticate.WebLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            loginLauncher.launch(intent);
            return Unit.INSTANCE;
        });
    }


    @Override
    public void onDestroyView() {
        if (liveCheckJob != null) {
            liveCheckJob.cancel(null);
            liveCheckJob = null;
        }
        gridFragment = null;
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
        super.onDestroyView();
    }

    private void initialize() {
        mainViewModel.initialize();
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
                Thumbnail th = new Thumbnail();
                th.setPath(event.getData().getIcon());
                // Find the stream in strms that belongs to this creator's subscription
                String creatorId = event.getData().getCreator();
                for (java.util.Map.Entry<Integer, Video> entry : mainViewModel.getStrms().entrySet()) {
                    Video stream = entry.getValue();
                    if (stream != null && stream.getCreator() != null
                            && creatorId.equalsIgnoreCase(stream.getCreator().getId())) {
                        stream.setThumbnail(th);
                        android.app.Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> addToRow(stream));
                        }
                        break;
                    }
                }
            } else if (eventType.equalsIgnoreCase("CONTENT_POST_RELEASE")) {
                dLog("SOCKET", "CONTENT_POST_RELEASE");
                if (event.getData().getVideo() == null
                        || event.getData().getVideo().getGuid() == null) return;
                client.getVideoObject(event.getData().getVideo().getGuid(), video -> {
                    android.app.Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> addToRow(video));
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
        authManager.clearTokens();

        if (clearWebCookies) {
            // Full logout: wipe the WebView cookie jar so the next WebLoginActivity
            // session starts clean and can log in as a different account.
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            android.webkit.CookieManager.getInstance().flush();
        }

        // Clear all ViewModel-owned model fields (also cancels retry handler and increments loadGeneration)
        mainViewModel.clearForLogout();

        // Reset Fragment-owned UI/adapter state
        rowSelected = 0;
        colSelected = 0;

        // Cancel any in-flight live-check coroutine
        if (liveCheckJob != null) {
            liveCheckJob.cancel(null);
            liveCheckJob = null;
        }

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

        // Reset grid fragment state
        gridFragment = null;
        pendingBrowseVideos = null;
        subRowAdapters.clear();
        rowsAdapter = null;
        nextRowId = 1;
        settingsRowIndex = -1;

        // Reset UI initialization flag to allow proper setup on next login
        uiInitialized = false;

        // Mark as logged out to prevent callbacks from updating UI
        isLoggedIn = false;

        // Restart the web login flow instead of closing the app
        checkLogin();
    }

    private void initRows() {
        if (!isAdded() || getActivity() == null || !isLoggedIn) return;
        List<Video> allVideos = mainViewModel.getMergedVideos();
        nextRowId = 1;

        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        rowsAdapter.add(new PageRow(new HeaderItem(0, getString(R.string.browse))));

        // Only create rows for creators whose names are already known.
        // addOrUpdateSubRow() will insert the remaining rows as videos arrive.
        for (Subscription sub : mainViewModel.getSubscriptions()) {
            String creatorGUID = sub.getCreator();
            if (creatorGUID == null) continue;
            String name = mainViewModel.getCreatorNames().get(creatorGUID);
            if (name == null || name.isEmpty()) continue;

            ArrayObjectAdapter subAdapter = new ArrayObjectAdapter(new CardPresenter(new ArrayList<>()));
            List<Video> subVideos = mainViewModel.getVideosFor(creatorGUID);
            if (subVideos != null) {
                for (Video v : subVideos) subAdapter.add(v);
            }
            subRowAdapters.put(creatorGUID, subAdapter);
            rowsAdapter.add(new ListRow(new CreatorHeaderItem(nextRowId++, name, creatorGUID), subAdapter));
        }

        // Track Settings row index before adding it so addOrUpdateSubRow() can insert before it safely.
        settingsRowIndex = rowsAdapter.size();
        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter settingsAdapter = new ArrayObjectAdapter(mGridPresenter);
        settingsAdapter.add(getResources().getString(R.string.refresh));
        settingsAdapter.add(getResources().getString(R.string.live_stream));
        settingsAdapter.add(getResources().getString(R.string.app_info));
        settingsAdapter.add(getResources().getString(R.string.logout));
        rowsAdapter.add(new ListRow(new HeaderItem(nextRowId++, getString(R.string.settings)), settingsAdapter));

        setAdapter(rowsAdapter);
        pendingBrowseVideos = allVideos;

        if (getView() != null) getView().post(() -> setSelectedPosition(rowSelected, false, null));
    }

    private void addOrUpdateSubRow(String creatorGUID) {
        if (rowsAdapter == null) return;
        ArrayObjectAdapter existing = subRowAdapters.get(creatorGUID);
        if (existing != null) {
            // Row already created — just refresh its content.
            updateSubRow(creatorGUID);
            return;
        }
        String name = mainViewModel.getCreatorNames().get(creatorGUID);
        if (name == null || name.isEmpty()) return; // Name not yet available
        ArrayObjectAdapter subAdapter = new ArrayObjectAdapter(new CardPresenter(new ArrayList<>()));
        List<Video> subVideos = mainViewModel.getVideosFor(creatorGUID);
        if (subVideos != null) for (Video v : subVideos) subAdapter.add(v);
        subRowAdapters.put(creatorGUID, subAdapter);
        // Insert before Settings. settingsRowIndex tracks the exact position — don't rely
        // on size()-1 since that assumption breaks if Browse (row 0) is the only other row.
        int insertIdx = (settingsRowIndex >= 1) ? settingsRowIndex : Math.max(1, rowsAdapter.size() - 1);
        rowsAdapter.add(insertIdx, new ListRow(new CreatorHeaderItem(nextRowId++, name, creatorGUID), subAdapter));
        settingsRowIndex++;
    }

    private void updateSubRow(String creatorGUID) {
        ArrayObjectAdapter adapter = subRowAdapters.get(creatorGUID);
        if (adapter == null) return;
        List<Video> subVideos = mainViewModel.getVideosFor(creatorGUID);
        if (subVideos == null) return;
        adapter.clear();
        for (Video v : subVideos) adapter.add(v);
    }

    private void addLiveToRow(Video stream) {
        // Browse-only layout: all content goes into row 0 (Browse).
        addToBrowseRow(stream);
    }

    private void setupLiveCheck() {
        if (mainViewModel.getStrms().isEmpty()) return;
        liveCheckJob = MainFragmentExtKt.startLiveCheckLoop(
            getViewLifecycleOwner(),
            mainViewModel.getStrms(),
            client,
            stream -> addLiveToRow(stream)
        );
    }

    private void appendVideosToRows(String creatorGUID) {
        if (!isLoggedIn) return;
        List<Video> allVideos = mainViewModel.getMergedVideos();
        if (gridFragment != null) {
            gridFragment.appendUniqueVideos(allVideos);
        }
        updateSubRow(creatorGUID);
    }

    private void addToRow(Video video) {
        // Browse-only layout: all content goes into row 0 (Browse).
        dLog("addToRow", video.getGuid());
        addToBrowseRow(video);
    }

    private void addToBrowseRow(Video video) {
        if (gridFragment != null) {
            gridFragment.prependVideo(video);
        }
    }

    void onGridFragmentReady(BrowseGridFragment frag) {
        gridFragment = frag;
        if (pendingBrowseVideos != null && !pendingBrowseVideos.isEmpty()) {
            frag.replaceVideos(pendingBrowseVideos, mainViewModel.getVideoProgress());
            pendingBrowseVideos = null;
        }
    }

    private void onGridNearEnd() {
        if (!isLoggedIn) return;
        mainViewModel.loadNextPage();
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
        // Item selection for the Settings ListRow (no-op for videos; grid handles its own selection)
        setOnItemViewSelectedListener((itemViewHolder, item, rowViewHolder, row) -> {});
    }

    private Unit onVideoSelected(@Nullable Presenter.ViewHolder itemViewHolder, @NonNull Video video) {
        if (itemViewHolder != null) {
            Activity activity = getActivity();
            if (activity == null || !isAdded()) return Unit.INSTANCE;

            // Get intent to switch to DetailActivity ready
            Intent intent = new Intent(activity, DetailsActivity.class);

            // Setup transition animation to detail screen
            ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            activity,
                            itemViewHolder.view.findViewById(R.id.image),
                            DetailsActivity.SHARED_ELEMENT_NAME);

            if ("live".equalsIgnoreCase(video.getType())) {
                intent.putExtra(DetailsActivity.Video, video);
                activity.startActivity(intent, activityOptions.toBundle());
            } else {
                // getVideoId() returns attachmentOrder[0] from the list response, which may be
                // absent or may contain a non-video attachment ID. getPost gives us the canonical
                // videoAttachments[0].guid that /api/v3/content/video and /api/v3/delivery/info
                // actually require — the same lookup VideoDetailsFragment.ACTION_RES already uses.
                client.getPost(video.getGuid(), post -> {
                    Activity act = getActivity();
                    if (act == null || !isAdded()) return Unit.INSTANCE;
                    if (post == null) {
                        Toast.makeText(act, "Could not load video", Toast.LENGTH_SHORT).show();
                        return Unit.INSTANCE;
                    }
                    List<VideoAttachments> attachments = post.getVideoAttachments();
                    if (attachments == null || attachments.isEmpty()) {
                        Toast.makeText(act, "Could not load video", Toast.LENGTH_SHORT).show();
                        return Unit.INSTANCE;
                    }
                    String videoId = attachments.get(0).getGuid();
                    // Patch so getVideo's entityId param is also correct
                    video.setAttachmentIds(new String[]{ videoId });
                    client.getVideoInfo(videoId, videoInfo -> {
                        Activity act2 = getActivity();
                        if (act2 == null || !isAdded()) return Unit.INSTANCE;
                        if (videoInfo == null) {
                            Toast.makeText(act2, "Could not load video", Toast.LENGTH_SHORT).show();
                            return Unit.INSTANCE;
                        }
                        String res = getHighestSupportedRes(videoInfo);
                        client.getVideo(video, res, newVideo -> {
                            Activity act3 = getActivity();
                            if (act3 == null || !isAdded()) return Unit.INSTANCE;
                            if (newVideo.getVidUrl() == null || newVideo.getVidUrl().isEmpty()) {
                                Toast.makeText(act3, "Video URL unavailable", Toast.LENGTH_SHORT).show();
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
                // Clear ViewModel model state (increments loadGeneration)
                mainViewModel.clearForLogout();
                // Clear Fragment adapter/UI state
                subRowAdapters.clear();
                rowsAdapter = null;
                nextRowId = 1;
                settingsRowIndex = -1;
                if (getAdapter() != null) setAdapter(null);
                gridFragment = null;
                pendingBrowseVideos = null;
                rowSelected = 0;
                colSelected = 0;
                mainViewModel.initialize();
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
                .setPositiveButton("OK", null)
                .create()
                .show();
    }

    private void selectLivestream() {
        List<String> subs = new ArrayList<>();
        for (Subscription s : mainViewModel.getSubscriptions()) {
            if (s.getPlan() != null) {
                subs.add(s.getPlan().getTitle());
            }
        }
        CharSequence[] s = subs.toArray(new CharSequence[0]);
        new AlertDialog.Builder(getContext())
                .setTitle("Play livestream?")
                .setItems(s, (dialog, which) -> {
                    Activity activity = getActivity();
                    if (activity == null || !isAdded()) return;
                    String stream = mainViewModel.getSubscriptions().get(which).getStreamUrl();
                    if (stream != null) {
                        dLog("LIVE", stream);
                        Video live = new Video();
                        live.setVidUrl(stream);
                        Intent intent = new Intent(activity, PlaybackActivity.class);
                        intent.putExtra(DetailsActivity.Video, live);
                        startActivity(intent);
                    } else {
                        Toast.makeText(activity, "Subscription does not include access to livestream.", Toast.LENGTH_LONG).show();
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
        info.getLevels().forEach(level -> {
            if (level.getName().equalsIgnoreCase(Integer.toString(y))) {
                found.set(true);
            }
        });
        String res;
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