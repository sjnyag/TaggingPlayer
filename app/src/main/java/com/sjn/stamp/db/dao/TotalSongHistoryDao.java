package com.sjn.stamp.db.dao;

import com.sjn.stamp.db.SongHistory;
import com.sjn.stamp.db.TotalSongHistory;

import java.util.List;

import io.realm.Realm;
import io.realm.Sort;

public class TotalSongHistoryDao extends BaseDao {

    private static TotalSongHistoryDao sInstance;

    public static TotalSongHistoryDao getInstance() {
        if (sInstance == null) {
            sInstance = new TotalSongHistoryDao();
        }
        return sInstance;
    }

    public List<TotalSongHistory> getOrderedList(Realm realm) {
        return realm.where(TotalSongHistory.class).greaterThanOrEqualTo("mPlayCount", 1).findAll().sort("mPlayCount", Sort.DESCENDING);
    }

    public int saveOrIncrement(Realm realm, final TotalSongHistory rawTotalSongHistory) {
        int playCount;
        realm.beginTransaction();
        TotalSongHistory totalSongHistory = realm.where(TotalSongHistory.class)
                .equalTo("mSong.mMediaId", rawTotalSongHistory.getSong().getMediaId())
                .findFirst();
        if (totalSongHistory == null) {
            rawTotalSongHistory.setId(getAutoIncrementId(realm, TotalSongHistory.class));
            rawTotalSongHistory.setSong(SongDao.getInstance().findOrCreate(realm, rawTotalSongHistory.getSong()));
            realm.insert(rawTotalSongHistory);
            playCount = rawTotalSongHistory.getPlayCount();
        } else {
            totalSongHistory.incrementPlayCount(rawTotalSongHistory.getPlayCount());
            totalSongHistory.incrementSkipCount(rawTotalSongHistory.getSkipCount());
            totalSongHistory.incrementCompleteCount(rawTotalSongHistory.getCompleteCount());
            playCount = totalSongHistory.getPlayCount();
        }
        realm.commitTransaction();
        return playCount;
    }

    public void save(Realm realm, final long songQueueId, final int playCount, final int skipCount, final int completeCount) {
        realm.beginTransaction();
        SongHistory songHistory = realm.where(SongHistory.class).equalTo("mId", songQueueId).findFirst();
        if (songHistory == null) {
            realm.cancelTransaction();
            return;
        }
        TotalSongHistory totalSongHistory = realm.where(TotalSongHistory.class)
                .equalTo("mSong.mMediaId", songHistory.getSong().getMediaId())
                .findFirst();
        if (totalSongHistory == null) {
            totalSongHistory = realm.createObject(TotalSongHistory.class, getAutoIncrementId(realm, TotalSongHistory.class));
            totalSongHistory.setSong(SongDao.getInstance().findOrCreate(realm, songHistory.getSong()));
        }
        totalSongHistory.setPlayCountIfOver(playCount);
        totalSongHistory.setSkipCountIfOver(skipCount);
        totalSongHistory.setCompleteCountIfOver(completeCount);
        realm.commitTransaction();
    }

    public TotalSongHistory newStandalone() {
        return new TotalSongHistory();
    }
}