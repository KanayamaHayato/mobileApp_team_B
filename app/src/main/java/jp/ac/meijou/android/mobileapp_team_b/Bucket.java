package jp.ac.meijou.android.mobileapp_team_b;

import android.net.Uri;

public class Bucket {
    public String bucketId;
    public String bucketName;
    public int count;
    public Uri coverUri; // 表紙に使う1枚

    //画像を表示するためのモデル
    public Bucket(String bucketId, String bucketName, int count, Uri coverUri) {
        this.bucketId = bucketId;
        this.bucketName = bucketName;
        this.count = count;
        this.coverUri = coverUri;
    }
}
