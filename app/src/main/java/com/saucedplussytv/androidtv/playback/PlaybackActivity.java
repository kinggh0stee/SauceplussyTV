package com.saucedplussytv.androidtv.playback;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.IntentCompat;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import kotlin.Unit;
import com.saucedplussytv.androidtv.R;
import com.saucedplussytv.androidtv.authenticate.AuthManager;
import com.saucedplussytv.androidtv.browse.MainFragment;
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient;
import com.saucedplussytv.androidtv.detail.DetailsActivity;
import com.saucedplussytv.androidtv.models.Video;

@OptIn(markerClass = UnstableApi.class)
public class PlaybackActivity extends FragmentActivity {

    private SaucedplussyTVClient client;

    private PlayerView playerView;
    private ImageView like;
    private ImageView dislike;
    private ImageView menu;
    private ImageView speed;
    private ImageView subtitles;
    private LinearLayout exo_playback_menu;
    private LinearLayout exo_settings_menu;
    private ExoPlayer player;
    private MediaSession mediaSession;

    private boolean playWhenReady = true;
    private int currentWindow = 0;
    private long playbackPosition = 0;
    private boolean resumed = false;
    private boolean playerInitialized = false;
    private boolean initializationInProgress = false;

    private String url = "";
    private Video video;

    @SuppressLint("MissingInflatedId")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        client = SaucedplussyTVClient.Companion.getInstance(this, getPreferences(Context.MODE_PRIVATE));
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final Video video = IntentCompat.getSerializableExtra(getIntent(), DetailsActivity.Video, Video.class);
        this.video = video;
        if (video == null) {
            finish();
            return;
        }
        url = video.getVidUrl();

        playerView = findViewById(R.id.exoplayer);
        ((TextView) findViewById(R.id.exo_title)).setText(video.getTitle());
        like = findViewById(R.id.exo_like);
        dislike = findViewById(R.id.exo_dislike);
        menu = findViewById(R.id.exo_menu);
        exo_playback_menu = findViewById(R.id.exo_playback_menu);
        exo_settings_menu = findViewById(R.id.exo_settings_menu);
        speed = findViewById(R.id.exo_speed);
        subtitles = findViewById(R.id.exo_subtitles);
        setupLikeAndDislike();
        setupMenu();

        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            if (visibility != View.VISIBLE) {
                exo_playback_menu.setVisibility(View.VISIBLE);
                exo_settings_menu.setVisibility(View.GONE);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (playerView.isControllerFullyVisible()) {
                    if (exo_playback_menu.getVisibility() == View.VISIBLE) {
                        playerView.hideController();
                    } else {
                        exo_settings_menu.setVisibility(View.GONE);
                        exo_playback_menu.setVisibility(View.VISIBLE);
                    }
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!playerInitialized && !initializationInProgress && player == null) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // onStart handles init for API 24+; this guards the rare case player is null on resume
        if (player == null && !playerInitialized && !initializationInProgress) {
            initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (initializationInProgress) {
            initializationInProgress = false;
        }
        playerInitialized = false;
        saveVideoPosition();
        releasePlayer();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // See whether the player view wants to handle media or DPAD keys events.
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }


    private void setupLikeAndDislike() {
        client.getPost(video.getId(), post -> {
            if (post == null || post.getUserInteractions() == null) return Unit.INSTANCE;
            if (!post.getUserInteractions().isEmpty()) {
                if (post.isLiked()) {
                    like.setImageResource(R.drawable.ic_like);
                } else if (post.isDisliked()) {
                    dislike.setImageResource(R.drawable.ic_dislike);
                }
            }

            return Unit.INSTANCE;
        });

        like.setOnClickListener(v -> client.toggleLikePost(video.getId(), liked -> {
            if (liked) {
                like.setImageResource(R.drawable.ic_like);
            } else {
                like.setImageResource(R.drawable.ic_like_unselected);
            }

            dislike.setImageResource(R.drawable.ic_dislike_unselected);
            return Unit.INSTANCE;
        }));
        dislike.setOnClickListener(v -> client.toggleDislikePost(video.getId(), disliked -> {
            if (disliked) {
                dislike.setImageResource(R.drawable.ic_dislike);
            } else {
                dislike.setImageResource(R.drawable.ic_dislike_unselected);
            }

            like.setImageResource(R.drawable.ic_like_unselected);
            return Unit.INSTANCE;
        }));
    }

    private void setupMenu() {
        // Show settings menu
        menu.setOnClickListener(v -> {
            exo_playback_menu.setVisibility(View.GONE);
            exo_settings_menu.setVisibility(View.VISIBLE);
        });

        speed.setOnClickListener(v -> showSpeedDialog());
        subtitles.setOnClickListener(v -> showSubtitleDialog());
    }

    private void showSpeedDialog() {
        PopupMenu speedMenu = new PopupMenu(this, speed);
        String[] playerSpeedArrayLabels = {"0.5x", "1.0x", "1.25x", "1.5x", "2.0x"};

        for (int i = 0; i < playerSpeedArrayLabels.length; i++) {
            speedMenu.getMenu().add(i, i, i, playerSpeedArrayLabels[i]);
        }

        speedMenu.setOnMenuItemClickListener(item -> {
            String itemTitle = item.getTitle().toString();
            float playbackSpeed = Float.parseFloat(itemTitle.substring(0, itemTitle.length() - 1));

            String msg = "Playback Speed: " + itemTitle;
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

            if (player != null) player.setPlaybackSpeed(playbackSpeed);
            return false;
        });

        speedMenu.show();
    }

    private void showSubtitleDialog() {
        if (player == null) return;

        Tracks tracks = player.getCurrentTracks();
        List<String> labels = new ArrayList<>();
        List<Tracks.Group> groups = new ArrayList<>();
        List<Integer> trackIndices = new ArrayList<>();

        labels.add("Off");
        groups.add(null);
        trackIndices.add(-1);

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format format = group.getTrackFormat(i);
                String label = format.label != null ? format.label
                        : format.language != null ? format.language
                        : "Track " + labels.size();
                labels.add(label);
                groups.add(group);
                trackIndices.add(i);
            }
        }

        if (labels.size() == 1) {
            Toast.makeText(this, "No subtitle tracks available", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentSelection = 0;
        for (int j = 1; j < labels.size(); j++) {
            if (groups.get(j).isTrackSelected(trackIndices.get(j))) {
                currentSelection = j;
                break;
            }
        }

        final int[] selected = {currentSelection};
        final List<Tracks.Group> finalGroups = groups;
        final List<Integer> finalIndices = trackIndices;

        new AlertDialog.Builder(this)
                .setTitle("Subtitles")
                .setSingleChoiceItems(labels.toArray(new String[0]), currentSelection,
                        (dialog, which) -> selected[0] = which)
                .setPositiveButton("OK",
                        (dialog, which) -> applySubtitleSelection(finalGroups, finalIndices, selected[0]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applySubtitleSelection(List<Tracks.Group> groups, List<Integer> indices, int selection) {
        if (player == null) return;
        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters()
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT);
        if (selection == 0) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(new TrackSelectionOverride(
                            groups.get(selection).getMediaTrackGroup(), indices.get(selection)));
        }
        player.setTrackSelectionParameters(builder.build());
    }

    private void initializePlayer() {
        initializationInProgress = true;

        if (url == null || url.isEmpty()) {
            initializationInProgress = false;
            Toast.makeText(this, "Video URL unavailable", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        AuthManager authManager = AuthManager.Companion.getInstance(this, getPreferences(Context.MODE_PRIVATE));
        authManager.withValidAccessToken(accessToken -> {
            // Check if initialization was cancelled or activity is no longer valid
            if (!initializationInProgress || isFinishing() || isDestroyed()) {
                initializationInProgress = false;
                return Unit.INSTANCE;
            }
            
            player = new ExoPlayer.Builder(this).build();
            player.setPlayWhenReady(playWhenReady);
            player.seekTo(currentWindow, playbackPosition);
            playerView.setPlayer(player);

            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
            // Sauce+ cookie-session auth: pass the session Cookie + matching User-Agent so the
            // CDN/Cloudflare accept the request. (accessToken carries the Cookie header value.)
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Cookie", accessToken);
            headers.put("User-Agent", authManager.getUserAgent());
            dataSourceFactory.setDefaultRequestProperties(headers);

            int flags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;
            DefaultHlsExtractorFactory extractorFactory = new DefaultHlsExtractorFactory(flags, true);
            MediaItem mi = MediaItem.fromUri(url);
            HlsMediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory).setExtractorFactory(extractorFactory).createMediaSource(mi);
            player.setMediaSource(hlsMediaSource);

            player.prepare();

            // Set up player listener
            player.addListener(new Player.Listener() {

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    MainFragment.dError("EXOPLAYER", error.getLocalizedMessage());
                    if (video != null) {
                        releasePlayer();
                        Toast.makeText(PlaybackActivity.this, "Video could not be played!", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    MainFragment.dLog("STATE", state + "");
                    switch (state) {
                        case Player.STATE_READY:
                            if (getIntent().getBooleanExtra(DetailsActivity.Resume, false) && !resumed
                                    && video.getVideoInfo() != null) {
                                player.seekTo(video.getVideoInfo().getProgress() * 1000);
                                resumed = true;
                            }
                            break;
                        case Player.STATE_ENDED:
                            saveVideoPosition();
                            releasePlayer();
                            finish();
                            break;
                        default:
                            break;
                    }
                }
            });

            // Only set up media session if activity is still in a valid state
            if (!isFinishing() && !isDestroyed()) {
                if (mediaSession != null) {
                    mediaSession.release();
                    mediaSession = null;
                }
                mediaSession = new MediaSession.Builder(PlaybackActivity.this, player).build();
                playerInitialized = true;
            } else {
                if (player != null) {
                    player.release();
                    player = null;
                }
            }
            
            initializationInProgress = false;
            return Unit.INSTANCE;
        }, () -> {
            initializationInProgress = false;
            Toast.makeText(this, "Session expired. Please relink your account.", Toast.LENGTH_LONG).show();
            finish();
            return Unit.INSTANCE;
        });
    }

    private void saveVideoPosition() {
        if (player != null && video != null
                && video.getVideoId() != null && !video.getVideoId().isEmpty()) {
            client.setVideoProgress(video.getVideoId(), (int) (player.getCurrentPosition() / 1000));
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playWhenReady = player.getPlayWhenReady();
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentMediaItemIndex();
            playerView.setPlayer(null);
            player.stop();
            player.release();
            player = null;
            if (mediaSession != null) {
                mediaSession.release();
                mediaSession = null;
            }
            playerInitialized = false;
            initializationInProgress = false;
            // Do NOT call finish() here — releasePlayer is also called on onPause/onStop
            // (backgrounding). finish() is called explicitly only when playback ends or errors.
        }
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}
