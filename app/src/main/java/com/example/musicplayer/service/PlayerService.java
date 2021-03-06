package com.example.musicplayer.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.musicplayer.R;
import com.example.musicplayer.app.Api;
import com.example.musicplayer.app.Constant;
import com.example.musicplayer.entiy.DownloadSong;
import com.example.musicplayer.entiy.HistorySong;
import com.example.musicplayer.entiy.LocalSong;
import com.example.musicplayer.entiy.Love;
import com.example.musicplayer.entiy.OnlineSong;
import com.example.musicplayer.entiy.Song;
import com.example.musicplayer.entiy.SongUrl;
import com.example.musicplayer.event.OnlineSongChangeEvent;
import com.example.musicplayer.event.OnlineSongErrorEvent;
import com.example.musicplayer.event.SongAlbumEvent;
import com.example.musicplayer.event.SongCollectionEvent;
import com.example.musicplayer.event.SongDownloadedEvent;
import com.example.musicplayer.event.SongHistoryEvent;
import com.example.musicplayer.event.SongListNumEvent;
import com.example.musicplayer.event.SongLocalEvent;
import com.example.musicplayer.event.SongStatusEvent;
import com.example.musicplayer.model.https.RetrofitFactory;
import com.example.musicplayer.util.CommonUtil;
import com.example.musicplayer.util.DownloadUtil;
import com.example.musicplayer.util.FileUtil;
import com.example.musicplayer.view.MainActivity;

import org.greenrobot.eventbus.EventBus;
import org.litepal.LitePal;
import org.litepal.crud.callback.FindMultiCallback;
import org.litepal.crud.callback.SaveCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@SuppressLint("NewApi")
public class PlayerService extends Service {

    private static final String TAG = "PlayerService";
    private final int NOTIFICATION_ID = 98;
    private PlayStatusBinder mPlayStatusBinder = new PlayStatusBinder();
    private MediaPlayer mediaPlayer = new MediaPlayer();       //?????????????????????
    private boolean isPause;                    //????????????
    private boolean isPlaying; //????????????
    private List<LocalSong> mLocalSongList;
    private List<OnlineSong> mSongList;
    private List<Love> mLoveList;
    private List<HistorySong> mHistoryList;
    private List<DownloadSong> mDownloadList;
    private int mCurrent;
    private int mListType;
    private int mPlayMode = Constant.PLAY_ORDER; //????????????,?????????????????????


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate: true");
        mListType = FileUtil.getSong().getListType();
        if (mListType == Constant.LIST_TYPE_ONLINE) {
            mSongList = LitePal.findAll(OnlineSong.class);
        } else if (mListType == Constant.LIST_TYPE_LOCAL) {
            mLocalSongList = LitePal.findAll(LocalSong.class);
        } else if (mListType == Constant.LIST_TYPE_LOVE) {
            mLoveList = LitePal.findAll(Love.class);
        } else if (mListType == Constant.LIST_TYPE_HISTORY) {
            mHistoryList = orderHistoryList(LitePal.findAll(HistorySong.class));
            //????????????????????????????????????????????????
            Song song = FileUtil.getSong();
            song.setPosition(0);
            FileUtil.saveSong(song);
        }else if(mListType == Constant.LIST_TYPE_DOWNLOAD){
            mDownloadList = orderDownloadList(DownloadUtil.getSongFromFile(Api.STORAGE_SONG_FILE));
        }

        //??????????????????
        startForeground(NOTIFICATION_ID,getNotification("??????????????????????????????????????????"));
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind: jsyjst");
        mediaPlayer.setOnCompletionListener(mp -> {
            EventBus.getDefault().post(new SongStatusEvent(Constant.SONG_PAUSE));//????????????
            mCurrent = FileUtil.getSong().getPosition();
            //??????????????????????????????
            if (mListType == Constant.LIST_TYPE_LOCAL) {
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mLocalSongList.size()); //????????????????????????????????????
                saveLocalSongInfo(mCurrent);
            } else if (mListType == Constant.LIST_TYPE_ONLINE) {
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mSongList.size());//????????????????????????????????????
                saveOnlineSongInfo(mCurrent);
            } else if (mListType == Constant.LIST_TYPE_LOVE) {
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mLoveList.size());//????????????????????????????????????
                saveLoveInfo(mCurrent);
            } else if(mListType == Constant.LIST_TYPE_HISTORY){
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mHistoryList.size());//????????????????????????????????????
                saveHistoryInfo(mCurrent);
            }else if(mListType == Constant.LIST_TYPE_DOWNLOAD){
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mDownloadList.size());//????????????????????????????????????
                saveDownloadInfo(mCurrent);
            }
            if(mListType!=0) {
                mPlayStatusBinder.play(mListType);
            }else {
                mPlayStatusBinder.stop();
            }
        });
        /**
         * MediaPlayer????????????setOnCompletionListener?????????
         * ????????????????????????????????????????????????????????????????????????true??????????????????onCompletion????????????
         */
        mediaPlayer.setOnErrorListener((mp, what, extra) -> true);
        return mPlayStatusBinder;
    }


    public class PlayStatusBinder extends Binder {

        public void setPlayMode(int mode){
            mPlayMode = mode;
        }


        public void getHistoryList() {
            mHistoryList = orderHistoryList(LitePal.findAll(HistorySong.class));
            //????????????????????????????????????????????????
            Song song = FileUtil.getSong();
            song.setPosition(0);
            FileUtil.saveSong(song);
        }

        /**
         * ????????????
         *
         * @param
         */

        public void play(int listType) {
            try {
                mListType = listType;
                if (mListType == Constant.LIST_TYPE_ONLINE) {
                    mSongList = LitePal.findAll(OnlineSong.class);
                    EventBus.getDefault().post(new SongAlbumEvent());
                } else if (mListType == Constant.LIST_TYPE_LOCAL) {
                    mLocalSongList = LitePal.findAll(LocalSong.class);
                    EventBus.getDefault().post(new SongLocalEvent()); //??????????????????????????????
                } else if (mListType == Constant.LIST_TYPE_LOVE) {
                    mLoveList = orderList(LitePal.findAll(Love.class));
                    EventBus.getDefault().post(new SongCollectionEvent(true));//????????????????????????
                } else if (mListType == Constant.LIST_TYPE_HISTORY) {
                    EventBus.getDefault().post(new SongHistoryEvent());  //??????????????????????????????
                }else if(mListType == Constant.LIST_TYPE_DOWNLOAD){
                    mDownloadList =orderDownloadList(DownloadUtil.getSongFromFile(Api.STORAGE_SONG_FILE));
                    EventBus.getDefault().post(new SongDownloadedEvent()); //?????????????????????????????????
                }
                mCurrent = FileUtil.getSong().getPosition();
                mediaPlayer.reset();//????????????????????????????????????
                if (mListType == Constant.LIST_TYPE_LOCAL) {
                    mediaPlayer.setDataSource(mLocalSongList.get(mCurrent).getUrl());
                    startPlay();
                } else if (mListType == Constant.LIST_TYPE_ONLINE) {
                    getSongUrl(mSongList.get(mCurrent).getSongId());
                } else if (mListType == Constant.LIST_TYPE_LOVE) {
                    mediaPlayer.setDataSource(mLoveList.get(mCurrent).getUrl());
                    startPlay();
                } else if(mListType == Constant.LIST_TYPE_HISTORY){
                    mediaPlayer.setDataSource(mHistoryList.get(mCurrent).getUrl());
                    startPlay();
                }else if(mListType == Constant.LIST_TYPE_DOWNLOAD){
                    Log.d(TAG, "play: "+mDownloadList.get(mCurrent).getUrl());
                    mediaPlayer.setDataSource(mDownloadList.get(mCurrent).getUrl());
                    startPlay();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //??????????????????
        public void playOnline() {
            try {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(FileUtil.getSong().getUrl());
                mediaPlayer.prepare();
                isPlaying = true;
                saveToHistoryTable();
                mediaPlayer.start();
                EventBus.getDefault().post(new OnlineSongChangeEvent()); //??????????????????????????????
                EventBus.getDefault().post(new SongStatusEvent(Constant.SONG_CHANGE));
                //?????????????????????
                Song song = FileUtil.getSong();
                getNotificationManager().notify(NOTIFICATION_ID,
                        getNotification(song.getSongName()+" - "+song.getSinger()));
            } catch (Exception e) {
                EventBus.getDefault().post(new OnlineSongErrorEvent());
                e.printStackTrace();
            }

        }


        /**
         * ????????????
         */

        public void pause() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                isPlaying = false;
                mediaPlayer.pause();
                isPause = true;
                EventBus.getDefault().post(new SongStatusEvent(Constant.SONG_PAUSE));//?????????????????????????????????
            }
        }

        public void resume() {
            if (isPause) {
                mediaPlayer.start();
                isPlaying = true;
                isPause = false;
                EventBus.getDefault().post(new SongStatusEvent(Constant.SONG_RESUME));
            }
        }


        public void next() {
            EventBus.getDefault().post(new SongStatusEvent(Constant.SONG_RESUME));
            mCurrent = FileUtil.getSong().getPosition();
            if (mListType == Constant.LIST_TYPE_LOCAL) {
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mLocalSongList.size()); //????????????????????????????????????
                saveLocalSongInfo(mCurrent);
            } else if (mListType == Constant.LIST_TYPE_ONLINE) {
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mSongList.size());//????????????????????????????????????
                saveOnlineSongInfo(mCurrent);
            } else if (mListType == Constant.LIST_TYPE_LOVE) {
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mLoveList.size());//????????????????????????????????????
                saveLoveInfo(mCurrent);
            } else if(mListType == Constant.LIST_TYPE_HISTORY){
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mHistoryList.size());//????????????????????????????????????
                saveHistoryInfo(mCurrent);
            }else if(mListType == Constant.LIST_TYPE_DOWNLOAD){
                mCurrent=getNextCurrent(mCurrent, mPlayMode, mDownloadList.size());//????????????????????????????????????
                saveDownloadInfo(mCurrent);
            }
            if(mListType!=0) mPlayStatusBinder.play(mListType);
        }

        public void last() {
            EventBus.getDefault().post(new SongStatusEvent(Constant.SONG_RESUME));//????????????
            mCurrent = FileUtil.getSong().getPosition();
            if (mListType == Constant.LIST_TYPE_LOCAL) {
                mCurrent = getLastCurrent(mCurrent,mPlayMode,mLocalSongList.size());
                saveLocalSongInfo(mCurrent);
            } else if (mListType == Constant.LIST_TYPE_ONLINE) {
                mCurrent = getLastCurrent(mCurrent,mPlayMode,mSongList.size());
                saveOnlineSongInfo(mCurrent);
            } else if (mListType == Constant.LIST_TYPE_LOVE) {
                mCurrent = getLastCurrent(mCurrent,mPlayMode,mLoveList.size());
                saveLoveInfo(mCurrent);
            } else if(mListType == Constant.LIST_TYPE_HISTORY){
                mCurrent = getLastCurrent(mCurrent,mPlayMode,mHistoryList.size());
                saveHistoryInfo(mCurrent);
            } else if(mListType == Constant.LIST_TYPE_DOWNLOAD){
                mCurrent = getLastCurrent(mCurrent,mPlayMode,mDownloadList.size());
                saveDownloadInfo(mCurrent);
            }
            if(mListType!=0) mPlayStatusBinder.play(mListType);
        }

        /**
         * ????????????
         */

        public void stop() {
            if (mediaPlayer != null) {
                isPlaying = false;
                mediaPlayer.stop();
                try {
                    mediaPlayer.prepare(); // ?????????stop???????????????????????????start????????????,??????????????????prepare??????
                } catch (Exception e) {
                    e.printStackTrace();
                }


            }

        }

        public boolean isPlaying() {

            return isPlaying;
        }

        public MediaPlayer getMediaPlayer() {

            return mediaPlayer;
        }
        public PlayerService getPlayerService(){
            return PlayerService.this;
        }

        public long getCurrentTime() {
            return mediaPlayer.getCurrentPosition() / 1000;
        }
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: ??????????????????");
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        stopForeground(true);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind: jsyjst");
        return true;
    }

    //?????????????????????????????????
    private void saveLocalSongInfo(int current) {
        //??????????????????????????????
        mLocalSongList = LitePal.findAll(LocalSong.class);
        Song song = new Song();
        LocalSong localSong = mLocalSongList.get(current);
        song.setPosition(current);
        song.setSongName(localSong.getName());
        song.setSinger(localSong.getSinger());
        song.setDuration(localSong.getDuration());
        song.setUrl(localSong.getUrl());
        song.setImgUrl(localSong.getPic());
        song.setSongId(localSong.getSongId());
        song.setQqId(localSong.getQqId());
        song.setOnline(false);
        song.setListType(Constant.LIST_TYPE_LOCAL);
        FileUtil.saveSong(song);
    }

    //?????????????????????????????????
    private void saveOnlineSongInfo(int current) {
        mSongList = LitePal.findAll(OnlineSong.class);
        Song song = new Song();
        song.setPosition(current);
        song.setSongId(mSongList.get(current).getSongId());
        song.setSongName(mSongList.get(current).getName());
        song.setSinger(mSongList.get(current).getSinger());
        song.setDuration(mSongList.get(current).getDuration());
        song.setUrl(mSongList.get(current).getUrl());
        song.setImgUrl(mSongList.get(current).getPic());
        song.setOnline(true);
        song.setListType(Constant.LIST_TYPE_ONLINE);
        song.setMediaId(mSongList.get(current).getMediaId());
        FileUtil.saveSong(song);
    }

    //????????????????????????????????????
    private void saveLoveInfo(int current) {
        mLoveList = orderList(LitePal.findAll(Love.class));
        Love love = mLoveList.get(current);
        Song song = new Song();
        song.setPosition(current);
        song.setSongId(love.getSongId());
        song.setQqId(love.getQqId());
        song.setSongName(love.getName());
        song.setSinger(love.getSinger());
        song.setUrl(love.getUrl());
        song.setImgUrl(love.getPic());
        song.setListType(Constant.LIST_TYPE_LOVE);
        song.setOnline(love.isOnline());
        song.setDuration(love.getDuration());
        song.setMediaId(love.getMediaId());
        song.setDownload(love.isDownload());
        FileUtil.saveSong(song);
    }


    //???????????????????????????
    private void saveDownloadInfo(int current){
        DownloadSong downloadSong = mDownloadList.get(current);
        Song song = new Song();
        song.setPosition(current);
        song.setSongId(downloadSong.getSongId());
        song.setSongName(downloadSong.getName());
        song.setSinger(downloadSong.getSinger());
        song.setUrl(downloadSong.getUrl());
        song.setImgUrl(downloadSong.getPic());
        song.setListType(Constant.LIST_TYPE_DOWNLOAD);
        song.setOnline(false);
        song.setDuration(downloadSong.getDuration());
        song.setMediaId(downloadSong.getMediaId());
        song.setDownload(true);
        FileUtil.saveSong(song);
    }

    //????????????????????????????????????
    private void saveHistoryInfo(int current) {
        HistorySong historySong = mHistoryList.get(current);
        Song song = new Song();
        song.setPosition(current);
        song.setSongId(historySong.getSongId());
        song.setQqId(historySong.getQqId());
        song.setSongName(historySong.getName());
        song.setSinger(historySong.getSinger());
        song.setUrl(historySong.getUrl());
        song.setImgUrl(historySong.getPic());
        song.setListType(Constant.LIST_TYPE_HISTORY);
        song.setOnline(historySong.isOnline());
        song.setDuration(historySong.getDuration());
        song.setMediaId(historySong.getMediaId());
        song.setDownload(historySong.isDownload());
        FileUtil.saveSong(song);
    }

    //?????????????????????????????????????????????
    private void saveToHistoryTable() {

        final Song song = FileUtil.getSong();
        LitePal.where("songId=?", song.getSongId()).findAsync(HistorySong.class)
                .listen(new FindMultiCallback<HistorySong>() {
                    @Override
                    public void onFinish(List<HistorySong> list) {
                        if (list.size() == 1) {
                            LitePal.deleteAll(HistorySong.class, "songId=?", song.getSongId());
                        }
                        final HistorySong history = new HistorySong();
                        history.setSongId(song.getSongId());
                        history.setQqId(song.getQqId());
                        history.setName(song.getSongName());
                        history.setSinger(song.getSinger());
                        history.setUrl(song.getUrl());
                        history.setPic(song.getImgUrl());
                        history.setOnline(song.isOnline());
                        history.setDuration(song.getDuration());
                        history.setMediaId(song.getMediaId());
                        history.setDownload(song.isDownload());
                        history.saveAsync().listen(new SaveCallback() {
                            @Override
                            public void onFinish(boolean success) {
                                if (success) {
                                    //????????????????????????????????????????????????
                                    EventBus.getDefault().post(new SongListNumEvent(Constant.LIST_TYPE_HISTORY));
                                    if (LitePal.findAll(HistorySong.class).size() > Constant.HISTORY_MAX_SIZE) {
                                        LitePal.delete(HistorySong.class, LitePal.findFirst(HistorySong.class).getId());
                                    }
                                }
                            }
                        });

                    }
                });

    }

    //??????????????????????????????
    private List orderList(List<Love> tempList) {
        List<Love> loveList = new ArrayList<>();
        loveList.clear();
        for (int i = tempList.size() - 1; i >= 0; i--) {
            loveList.add(tempList.get(i));
        }
        return loveList;
    }

    private List<DownloadSong> orderDownloadList(List<DownloadSong> tempList) {
        List<DownloadSong> downloadSongList = new ArrayList<>();
        downloadSongList.clear();
        for (int i = tempList.size() - 1; i >= 0; i--) {
            downloadSongList.add(tempList.get(i));
        }
        return downloadSongList;
    }

    private List<HistorySong> orderHistoryList(List<HistorySong> tempList) {
        List<HistorySong> historySongList = new ArrayList<>();
        historySongList.clear();
        for (int i = tempList.size() - 1; i >= 0; i--) {
            historySongList.add(tempList.get(i));
        }
        return historySongList;
    }

    //??????????????????????????????
    private void getSongUrl(String songId) {
        Log.d(TAG, "getSongUrl: "+ Api.SONG_URL_DATA_LEFT + songId + Api.SONG_URL_DATA_RIGHT);
        RetrofitFactory.createRequestOfSongUrl().getSongUrl(Api.SONG_URL_DATA_LEFT + songId + Api.SONG_URL_DATA_RIGHT)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new Observer<SongUrl>() {
                    @Override
                    public void onSubscribe(Disposable disposable) {

                    }

                    @Override
                    public void onNext(SongUrl songUrl) {
                        if (songUrl.getCode() == 0) {
                            String sip = songUrl.getReq_0().getData().getSip().get(0);
                            String purl = songUrl.getReq_0().getData().getMidurlinfo().get(0).getPurl();
                            if(purl.equals("")) {
                                CommonUtil.showToast(PlayerService.this,"?????????????????????????????????????????????????????????");
                                return;
                            }
                            Song song = FileUtil.getSong();
                            assert song != null;
                            song.setUrl(sip + purl);
                            FileUtil.saveSong(song);
                            try {
                                mediaPlayer.setDataSource(sip + purl);
                                Log.d(TAG, "onNext:jsyjst ="+sip+purl);
                                startPlay();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            Log.d(TAG, "onNext:" + songUrl.getCode() + ":??????????????????????????????");
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.d(TAG, "onError: "+throwable.toString());
                    }

                    @Override
                    public void onComplete() {

                    }
                });

    }

    //????????????
    private void startPlay() throws IOException {
        mediaPlayer.prepare();    //????????????
        isPlaying = true;
        mediaPlayer.start();
        saveToHistoryTable();
        EventBus.getDefault().post(new SongStatusEvent(Constant.SONG_CHANGE));//??????????????????????????????
        EventBus.getDefault().post(new OnlineSongChangeEvent()); //??????????????????????????????
        //?????????????????????
        Song song = FileUtil.getSong();
        getNotificationManager().notify(NOTIFICATION_ID,
                getNotification(song.getSongName()+" - "+song.getSinger()));
    }


    //????????????????????????????????????????????????
    private int getNextCurrent(int current, int playMode, int len) {
        int res;
        if (playMode == Constant.PLAY_ORDER) {
            res = (current + 1) % len;
        } else if (playMode == Constant.PLAY_RANDOM) {
            res = (current + (int) (Math.random() * len)) % len;
        } else {
            res = current;
        }
        return res;
    }
    //????????????????????????????????????????????????
    private int getLastCurrent(int current, int playMode, int len) {
        int res;
        if (playMode == Constant.PLAY_ORDER) {
            res = current - 1 == -1 ? len-1 : current - 1;
        } else if (playMode == Constant.PLAY_RANDOM) {
            res = (current + (int) (Math.random() * len)) % len;
        } else {
            res = current;
        }
        return res;
    }

    //??????????????????
    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }




    //?????????????????????
    private Notification getNotification(String title) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String id = "play";
            String name = "????????????";
            NotificationChannel mChannel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW);
            getNotificationManager().createNotificationChannel(mChannel);
            Notification.Builder builder = new Notification.Builder(this, id);
            builder.setSmallIcon(R.mipmap.icon);
            builder.setContentIntent(pi);
            builder.setContentTitle(title);
            return builder.build();
        } else {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "play");
            builder.setSmallIcon(R.mipmap.icon);
            builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.icon));
            builder.setContentIntent(pi);
            builder.setContentTitle(title);
            return builder.build();
        }

    }

}


