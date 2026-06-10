package com.saucedplussytv.androidtv.detail;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.leanback.app.DetailsSupportFragmentBackgroundController;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;
import kotlin.Unit;
import com.saucedplussytv.androidtv.R;
import com.saucedplussytv.androidtv.browse.MainActivity;
import com.saucedplussytv.androidtv.browse.MainFragment;
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient;
import com.saucedplussytv.androidtv.models.Level;
import com.saucedplussytv.androidtv.models.Video;
import com.saucedplussytv.androidtv.playback.PlaybackActivity;

@AndroidEntryPoint
public class VideoDetailsFragment extends DetailsSupportFragment {

    private static final String TAG = "VideoDetailsFragment";

    private static final int ACTION_PLAY = 1;
    private static final int ACTION_RESUME = 3;
    private static final int ACTION_RES = 2;

    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    @Inject
    SaucedplussyTVClient client;

    private VideoDetailsViewModel viewModel;

    private Video mSelectedMovie;

    private ArrayObjectAdapter mAdapter;
    private ClassPresenterSelector mPresenterSelector;

    private DetailsSupportFragmentBackgroundController mDetailsBackground;

    private static final String version = com.saucedplussytv.androidtv.BuildConfig.VERSION_NAME;
    private static final String userAgent = String.format("SaucedplussyTV %s (AndroidTV)", version);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDetailsBackground = new DetailsSupportFragmentBackgroundController(this);
        mSelectedMovie = IntentCompat.getSerializableExtra(getActivity().getIntent(), DetailsActivity.Video, Video.class);

        if (mSelectedMovie != null) {
            //String mSelectedUrl = getActivity().getIntent().getStringExtra("vidURL");
            mPresenterSelector = new ClassPresenterSelector();
            mAdapter = new ArrayObjectAdapter(mPresenterSelector);
            setupDetailsOverviewRow();
            setupDetailsOverviewRowPresenter();
            setAdapter(mAdapter);
            viewModel = new ViewModelProvider(this).get(VideoDetailsViewModel.class);
            viewModel.getLevels().observe(this, event -> {
                List<Level> levels = event.getContentIfNotHandled();
                if (levels != null) showResolutionDialog(levels);
            });
            viewModel.getDataError().observe(this, event -> {
                String msg = event.getContentIfNotHandled();
                if (msg == null) return;
                Activity a = getActivity();
                if (a != null && isAdded()) Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
            });
            setOnItemViewClickedListener(new ItemViewClickedListener());
        } else {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeBackground();
    }

    private void initializeBackground() {
        mDetailsBackground.enableParallax();
        client.getCreatorById(mSelectedMovie.getCreator().getId(), creator -> {
            Glide.with(requireActivity())
                    .asBitmap()
                    .load(new GlideUrl(creator.getCoverImage().getPath(), new LazyHeaders.Builder()
                            .addHeader("User-Agent", userAgent)
                            .build())
                        )
                    .override(1800, 519)
                    .centerCrop()
                    .error(R.drawable.default_background)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            mDetailsBackground.setCoverBitmap(resource);
                            mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {

                        }
                    });
            return Unit.INSTANCE;
        });
    }

    private void setupDetailsOverviewRow() {
        MainFragment.dLog(TAG, "doInBackground: " + mSelectedMovie.toString());
        final DetailsOverviewRow row = new DetailsOverviewRow(mSelectedMovie);
        row.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.default_background));
        Glide.with(requireActivity())
                .load(new GlideUrl(mSelectedMovie.getThumbnail().getPath(), new LazyHeaders.Builder()
                        .addHeader("User-Agent", userAgent)
                        .build())
                )
                .centerCrop()
                .transform(new RoundedCorners(48))
                .error(R.drawable.default_background)
                .into(new CustomTarget<Drawable>() {

                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        row.setImageDrawable(resource);
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }
                });

        ArrayObjectAdapter actionAdapter = new ArrayObjectAdapter();
        boolean isLive =  mSelectedMovie.getType().equalsIgnoreCase("live");

        // add RESUME first so it is the default
        if (!isLive) {

            if (mSelectedMovie.getVideoInfo().getProgress() > 0) {
                actionAdapter.add(new Action(ACTION_RESUME, getString(R.string.action_resume)));
            }
        }

        actionAdapter.add(new Action(ACTION_PLAY, getString(R.string.play)));

        if (!isLive) {
            actionAdapter.add(new Action(ACTION_RES, getString(R.string.resolutions)));
        }

        row.setActionsAdapter(actionAdapter);

        mAdapter.add(row);
    }

    private void setupDetailsOverviewRowPresenter() {
        // Set detail background.
        FullWidthDetailsOverviewRowPresenter detailsPresenter =
                new FullWidthDetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
        detailsPresenter.setBackgroundColor(
                ContextCompat.getColor(getContext(), R.color.default_background));

        // Hook up transition element.
        FullWidthDetailsOverviewSharedElementHelper sharedElementHelper =
                new FullWidthDetailsOverviewSharedElementHelper();
        sharedElementHelper.setSharedElementEnterTransition(
                getActivity(), DetailsActivity.SHARED_ELEMENT_NAME);
        detailsPresenter.setListener(sharedElementHelper);
        detailsPresenter.setParticipatingEntranceTransition(true);

        detailsPresenter.setOnActionClickedListener(action -> {
            if (action.getId() == ACTION_PLAY) {
                Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                intent.putExtra(DetailsActivity.Video, (Serializable) mSelectedMovie);
                intent.putExtra(DetailsActivity.Resume, false);
                startActivity(intent);
            } else if (action.getId() == ACTION_RESUME) {
                Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                intent.putExtra(DetailsActivity.Video, (Serializable) mSelectedMovie);
                intent.putExtra(DetailsActivity.Resume, true);
                startActivity(intent);
            } else if (action.getId() == ACTION_RES) {
                viewModel.loadLevels(mSelectedMovie.getGuid());
            } else {
                Activity activity = getActivity();
                if (activity == null || !isAdded()) return;
                Toast.makeText(activity, action.toString(), Toast.LENGTH_SHORT).show();
            }
        });
        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
    }

    private void showResolutionDialog(List<Level> levels) {
        Activity activity = getActivity();
        if (activity == null || !isAdded()) return;
        List<String> resolutions = new ArrayList<>();
        for (Level l : levels) {
            resolutions.add(l.getName());
        }
        CharSequence[] res = resolutions.toArray(new CharSequence[0]);
        new AlertDialog.Builder(activity)
                .setTitle("Resolutions")
                .setItems(res, (dialog, which) -> {
                    String chosen = resolutions.get(which);
                    viewModel.fetchVideoUrl(mSelectedMovie, chosen, newVideo -> {
                        Activity a = getActivity();
                        if (a == null || !isAdded()) return Unit.INSTANCE;
                        mSelectedMovie.setVidUrl(newVideo.getVidUrl());
                        Intent intent = new Intent(a, PlaybackActivity.class);
                        intent.putExtra(DetailsActivity.Video, (Serializable) mSelectedMovie);
                        a.startActivity(intent);
                        return Unit.INSTANCE;
                    });
                })
                .create().show();
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {

            if (item instanceof Video) {
                Activity activity = getActivity();
                if (activity == null || !isAdded()) return;
                MainFragment.dLog(TAG, "Item: " + item.toString());
                Intent intent = new Intent(activity, DetailsActivity.class);
                intent.putExtra(getResources().getString(R.string.movie), (Serializable) mSelectedMovie);

                Bundle bundle =
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                                activity,
                                itemViewHolder.view.findViewById(R.id.image),
                                DetailsActivity.SHARED_ELEMENT_NAME)
                                .toBundle();
                activity.startActivity(intent, bundle);
            }
        }
    }
}
