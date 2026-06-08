package com.saucedplussytv.androidtv.browse;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.VerticalGridSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.VerticalGridPresenter;
import com.saucedplussytv.androidtv.card.CardPresenter;
import com.saucedplussytv.androidtv.models.Video;
import com.saucedplussytv.androidtv.models.VideoProgress;
import java.util.ArrayList;
import java.util.List;

public class BrowseGridFragment extends VerticalGridSupportFragment
        implements BrowseSupportFragment.MainFragmentAdapterProvider {

    private final BrowseSupportFragment.MainFragmentAdapter<BrowseGridFragment> mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter<>(this);

    @Override
    public BrowseSupportFragment.MainFragmentAdapter getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }

    interface OnNearEndListener {
        void onNearEnd();
    }

    private static final int NUM_COLUMNS = 4;
    private CardPresenter cardPresenter;
    private ArrayObjectAdapter gridAdapter;
    private OnNearEndListener nearEndListener;

    List<Video> pendingVideos;
    List<VideoProgress> pendingProgress;

    void setNearEndListener(OnNearEndListener l) {
        nearEndListener = l;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VerticalGridPresenter presenter = new VerticalGridPresenter();
        presenter.setNumberOfColumns(NUM_COLUMNS);
        setGridPresenter(presenter);

        cardPresenter = new CardPresenter(pendingProgress != null ? pendingProgress : new ArrayList<>());
        gridAdapter = new ArrayObjectAdapter(cardPresenter);
        if (pendingVideos != null) {
            for (Video v : pendingVideos) gridAdapter.add(v);
            pendingVideos = null;
            pendingProgress = null;
        }
        setAdapter(gridAdapter);

        setOnItemViewSelectedListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (nearEndListener != null && item != null && gridAdapter.size() > 0) {
                int idx = gridAdapter.indexOf(item);
                if (idx >= gridAdapter.size() - NUM_COLUMNS * 3) {
                    nearEndListener.onNearEnd();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getParentFragment() instanceof MainFragment) {
            ((MainFragment) getParentFragment()).onGridFragmentReady(this);
        }
    }

    void replaceVideos(List<Video> videos, List<VideoProgress> progress) {
        if (gridAdapter == null) {
            pendingVideos = new ArrayList<>(videos);
            pendingProgress = progress;
            return;
        }
        cardPresenter = new CardPresenter(progress != null ? progress : new ArrayList<>());
        gridAdapter = new ArrayObjectAdapter(cardPresenter);
        for (Video v : videos) gridAdapter.add(v);
        setAdapter(gridAdapter);
    }

    void appendUniqueVideos(List<Video> videos) {
        if (gridAdapter == null || videos == null) return;
        for (Video v : videos) {
            if (!containsGuid(v.getGuid())) gridAdapter.add(v);
        }
    }

    void prependVideo(Video video) {
        if (gridAdapter == null || video == null) return;
        if (!containsGuid(video.getGuid())) gridAdapter.add(0, video);
    }

    void updateProgress(List<VideoProgress> progress) {
        if (cardPresenter == null) return;
        cardPresenter.updateProgress(progress != null ? progress : new ArrayList<>());
        if (gridAdapter != null && gridAdapter.size() > 0) {
            gridAdapter.notifyArrayItemRangeChanged(0, gridAdapter.size());
        }
    }

    private boolean containsGuid(String guid) {
        if (guid == null) return false;
        for (int i = 0; i < gridAdapter.size(); i++) {
            Object obj = gridAdapter.get(i);
            if (obj instanceof Video && guid.equalsIgnoreCase(((Video) obj).getGuid())) return true;
        }
        return false;
    }
}
