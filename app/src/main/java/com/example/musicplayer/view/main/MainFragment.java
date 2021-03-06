package com.example.musicplayer.view.main;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.musicplayer.R;
import com.example.musicplayer.adapter.ExpandableListViewAdapter;
import com.example.musicplayer.app.Api;
import com.example.musicplayer.app.Constant;
import com.example.musicplayer.entiy.AlbumCollection;
import com.example.musicplayer.entiy.HistorySong;
import com.example.musicplayer.entiy.LocalSong;
import com.example.musicplayer.entiy.Love;
import com.example.musicplayer.event.AlbumCollectionEvent;
import com.example.musicplayer.event.SongListNumEvent;
import com.example.musicplayer.util.DownloadUtil;
import com.example.musicplayer.view.main.collection.CollectionFragment;
import com.example.musicplayer.view.main.download.DownloadFragment;
import com.example.musicplayer.view.main.history.HistoryFragment;
import com.example.musicplayer.view.main.local.LocalFragment;
import com.example.musicplayer.view.search.AlbumContentFragment;
import com.example.musicplayer.widget.MyListView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.litepal.LitePal;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class MainFragment extends Fragment {

    private static final String TAG = "MainFragment";

    private MyListView myListView;
    private ExpandableListViewAdapter mAdapter;
    private LinearLayout mLocalMusicLinear, mCollectionLinear, mHistoryMusicLinear,mDownloadLinear;
    private TextView mLocalMusicNum, mLoveMusicNum, mHistoryMusicNum,mDownloadMusicNum;

    private TextView mSeekBtn;
    private List<List<AlbumCollection>> mAlbumCollectionList;
    private List<AlbumCollection> mLoveAlbumList;
    private boolean twoExpand;
    private String[] mGroupStrings = {"????????????", "????????????"};


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        EventBus.getDefault().register(this);
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        mLocalMusicLinear = view.findViewById(R.id.linear_local_music);
        mCollectionLinear = view.findViewById(R.id.linear_collection);
        LinearLayout mFunctionLinear = view.findViewById(R.id.linear_function);
        mHistoryMusicLinear = view.findViewById(R.id.linear_history);
        //????????????
        mFunctionLinear.setFocusableInTouchMode(true);
        myListView = view.findViewById(R.id.expand_lv_song_list);
        mSeekBtn = view.findViewById(R.id.tv_seek);
        mLocalMusicNum = view.findViewById(R.id.tv_local_music_num);
        mLoveMusicNum = view.findViewById(R.id.tv_love_num);
        mHistoryMusicNum = view.findViewById(R.id.tv_history_num);
        mDownloadMusicNum = view.findViewById(R.id.tv_download_num);
        mDownloadLinear = view.findViewById(R.id.downloadLinear);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        showAlbumList();
        onClick();
    }

    private void showAlbumList() {
        mLoveAlbumList = new ArrayList<>();
        mAlbumCollectionList = new ArrayList<>();
        AlbumCollection albumCollection = new AlbumCollection();
        albumCollection.setAlbumName("?????????");
        albumCollection.setSingerName("??????");
        mLoveAlbumList.add(albumCollection);
        mAlbumCollectionList.add(mLoveAlbumList);
        mAlbumCollectionList.add(orderCollection(LitePal.findAll(AlbumCollection.class)));
        mAdapter = new ExpandableListViewAdapter(getActivity(), mGroupStrings, mAlbumCollectionList);
        myListView.setAdapter(mAdapter);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        showMusicNum();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(AlbumCollectionEvent event) {
            mAlbumCollectionList.clear();
            mAlbumCollectionList.add(mLoveAlbumList);
            mAlbumCollectionList.add(orderCollection(LitePal.findAll(AlbumCollection.class)));
            //?????????????????????????????????????????????????????????????????????????????????
            if (twoExpand) {
                myListView.collapseGroup(1);
                myListView.expandGroup(1);
            } else {
                myListView.expandGroup(1);
                myListView.collapseGroup(1);
            }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSongListEvent(SongListNumEvent event){
        int type = event.getType();
        if(type == Constant.LIST_TYPE_HISTORY){
            mHistoryMusicNum.setText(String.valueOf(LitePal.findAll(HistorySong.class).size()));
        }else if(type == Constant.LIST_TYPE_LOCAL){
            mLocalMusicNum.setText(String.valueOf(LitePal.findAll(LocalSong.class).size()));
        }else if(type == Constant.LIST_TYPE_DOWNLOAD){
            mDownloadMusicNum.setText(String.valueOf(DownloadUtil.getSongFromFile(Api.STORAGE_SONG_FILE).size()));
        }
    }



    private void onClick() {
        //????????????
        mLocalMusicLinear.setOnClickListener(v -> replaceFragment(new LocalFragment()));
        //??????
        mSeekBtn.setOnClickListener(v -> replaceFragment(new AlbumContentFragment.SearchFragment()));
        //????????????
        mCollectionLinear.setOnClickListener(v -> replaceFragment(new CollectionFragment()));
        //??????
        mDownloadLinear.setOnClickListener(view -> replaceFragment(new DownloadFragment()));
        //????????????
        mHistoryMusicLinear.setOnClickListener(v -> replaceFragment(new HistoryFragment()));

        //??????????????????
        myListView.setOnGroupExpandListener(groupPosition -> {
            if (groupPosition == 1) {
                twoExpand = true;
            }
        });

        //??????????????????
        myListView.setOnGroupCollapseListener(groupPosition -> {
            if (groupPosition == 1) {
                twoExpand = false;
            }
        });
        myListView.setOnGroupClickListener((parent, v, groupPosition, id) -> false);
        //???????????????item????????????
        mAdapter.setOnChildItemClickListener((groupPosition, childPosition) -> {
            //?????????????????????????????????????????????????????????????????????????????????????????????
            if (groupPosition == 0 && childPosition == 0) {
                replaceFragment(new CollectionFragment());
            } else if (groupPosition == 1) {
                //?????????????????????????????????????????????????????????????????????fragment
                AlbumCollection albumCollection = mAlbumCollectionList.get(groupPosition).get(childPosition);
                replaceFragment(AlbumContentFragment.newInstance(
                        albumCollection.getAlbumId(),
                        albumCollection.getAlbumName(),
                        albumCollection.getAlbumPic(),
                        albumCollection.getSingerName(),
                        albumCollection.getPublicTime()
                ));
            }
        });
    }


    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        //?????????????????????
        transaction.setCustomAnimations(R.anim.fragment_in, R.anim.fragment_out, R.anim.slide_in_right, R.anim.slide_out_right);
        transaction.add(R.id.fragment_container, fragment);
        transaction.hide(this);
        //???????????????????????????
        transaction.addToBackStack(null);
        transaction.commit();
    }

    //????????????
    private void showMusicNum() {
        mLocalMusicNum.setText(String.valueOf(LitePal.findAll(LocalSong.class).size()));
        mLoveMusicNum.setText(String.valueOf(LitePal.findAll(Love.class).size()));
        mHistoryMusicNum.setText(String.valueOf(LitePal.findAll(HistorySong.class).size()));
        mDownloadMusicNum.setText(String.valueOf(DownloadUtil.getSongFromFile(Api.STORAGE_SONG_FILE).size()));
    }

    //????????????????????????????????????
    private List<AlbumCollection> orderCollection(List<AlbumCollection> tempList) {
        List<AlbumCollection> mAlbumCollectionList = new ArrayList<>();
        for (int i = tempList.size() - 1; i >= 0; i--) {
            mAlbumCollectionList.add(tempList.get(i));
        }
        return mAlbumCollectionList;
    }
}
