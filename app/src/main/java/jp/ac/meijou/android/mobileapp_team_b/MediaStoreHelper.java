package jp.ac.meijou.android.mobileapp_team_b;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.*;
import jp.ac.meijou.android.mobileapp_team_b.Bucket;

public class MediaStoreHelper {
    // フォルダ（アルバム）一覧を新しい順に作る
    public static List<Bucket> queryBuckets(Context ctx) {
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN
        };
        String orderBy = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        Map<String, Bucket> map = new LinkedHashMap<>();

        try (Cursor c = ctx.getContentResolver().query(collection, projection, null, null, orderBy)) {
            if (c == null) return new ArrayList<>();
            int colId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int colBid = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
            int colBname = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);

            while (c.moveToNext()) {
                long id = c.getLong(colId);
                String bucketId = c.getString(colBid);
                String bucketName = c.getString(colBname);
                Uri photoUri = ContentUris.withAppendedId(collection, id);

                Bucket b = map.get(bucketId);
                if (b == null) {
                    b = new Bucket(bucketId, bucketName, 1, photoUri); // 最初に見つけた1枚を表紙に
                    map.put(bucketId, b);
                } else {
                    b.count++;
                }
            }
        }
        return new ArrayList<>(map.values());
    }
}
