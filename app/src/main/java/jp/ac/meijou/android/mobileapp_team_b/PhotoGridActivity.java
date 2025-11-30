package jp.ac.meijou.android.mobileapp_team_b;

import android.app.RecoverableSecurityException;
import android.content.ContentUris;
import android.content.IntentSender;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import java.util.HashSet;
import java.util.Set;
import android.content.ContentValues;
import android.os.Build;
import android.provider.MediaStore;

public class PhotoGridActivity extends AppCompatActivity {

    private final List<Uri> data = new ArrayList<>();
    private RecyclerView recycler;
    private PhotoAdapter adapter;

    @Override // フォルダ一覧画面（FolderFragment）でタップされたフォルダの情報を受け取る
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_photo_grid);

        String bucketId = getIntent().getStringExtra("bucketId");
        String bucketName = getIntent().getStringExtra("bucketName");
        setTitle(bucketName == null ? "Photos" : bucketName);

        recycler = findViewById(R.id.recyclerPhotos);
        // 3列のグリッド表示にする
        recycler.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new PhotoAdapter();
        recycler.setAdapter(adapter);

        if (bucketId == null) {
            Toast.makeText(this, "bucketId がありません", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadPhotos(bucketId);
    }

    private void loadPhotos(String bucketId) {
        new Thread(() -> {
            List<Uri> list = queryPhotosInBucket(bucketId);
            runOnUiThread(() -> {
                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    // 指定のフォルダIDとおなじ写真だけを持ってくる
    private List<Uri> queryPhotosInBucket(String bucketId) {
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{ MediaStore.Images.Media._ID };
        String selection = MediaStore.Images.Media.BUCKET_ID + "=?";
        String[] args = new String[]{ bucketId };
        // 撮影日が新しい順 (DESC)
        String orderBy = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        List<Uri> result = new ArrayList<>();
        try (var c = getContentResolver().query(collection, projection, selection, args, orderBy)) {
            if (c == null) return result;
            int colId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (c.moveToNext()) {
                long id = c.getLong(colId);
                result.add(ContentUris.withAppendedId(collection, id));
            }
        }
        return result;
    }

    //最小アダプタ
    class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView img;
            VH(View v) { super(v); img = v.findViewById(R.id.img); }
        }
        // ここで item_photo.xmlを読み込む
        @Override public VH onCreateViewHolder(ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext()).inflate(R.layout.item_photo, p, false);
            return new VH(view);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            Uri uri = data.get(pos);
            Glide.with(h.img.getContext()).load(uri).centerCrop().into(h.img);
            //拡大へ飛ばしたいならここでIntent
            h.itemView.setOnClickListener(v ->
                    Toast.makeText(v.getContext(), uri.toString(), Toast.LENGTH_SHORT).show()
            );

            // 長押し時の処理
            h.itemView.setOnLongClickListener(v -> {
                showMoveDialog(uri, pos); // 移動用のダイアログを出す
                return true; // "処理完了"を返す（通常のクリック処理をキャンセル）
            });

        }
        @Override public int getItemCount() { return data.size(); }
    }

    // URIから実際のファイルパスを入手するメソッド
    private String getPathFromUri(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // URIからファイル名を取得するメソッド
    private String getFileName(Uri uri){
        String result = null;
        // データベース管理されたデータであることを確認
        if (uri.getScheme().equals("content")){
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[]{MediaStore.Images.Media.DISPLAY_NAME},null, null, null)){
                if (cursor != null && cursor.moveToFirst()){
                    int index = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            }
        }
        if (result == null){
            result = uri.getLastPathSegment();
        }
        return result;
    }



    // 移動先のフォルダを選ぶダイアログを表示
    private void showMoveDialog(Uri imageUri, int position) {
        // 1. まずは既存のデータベースからフォルダを探す（画像が入っているフォルダ）
        List<Bucket> buckets = MediaStoreHelper.queryBuckets(this);

        // 2. 重複を防ぐために、名前のリストを作っておく
        Set<String> existingNames = new HashSet<>();
        for (Bucket b : buckets) {
            existingNames.add(b.bucketName);
        }

        // 3. "Pictures" ディレクトリの中にあるフォルダを直接探しに行く（空のフォルダを見つけるため）
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File[] subFolders = picturesDir.listFiles(File::isDirectory); // フォルダだけ取得

        if (subFolders != null) {
            for (File folder : subFolders) {
                String name = folder.getName();
                // もしデータベースのリストに含まれていないフォルダがあれば、手動で追加する
                if (!existingNames.contains(name)) {
                    Bucket emptyBucket = new Bucket();
                    emptyBucket.bucketName = name;
                    emptyBucket.bucketId = "MANUAL_" + name; // IDは適当でOK（移動処理では名前しか使わないため）
                    buckets.add(emptyBucket);
                }
            }
        }

        // 4. ダイアログ表示用のリスト（文字配列）を作成
        String[] folderNames = new String[buckets.size()];
        for (int i = 0; i < buckets.size(); i++) {
            folderNames[i] = buckets.get(i).bucketName;
        }

        // 5. ダイアログを表示
        new AlertDialog.Builder(this)
                .setTitle("移動先のフォルダを選択")
                .setItems(folderNames, (dialog, which) -> {
                    Bucket targetBucket = buckets.get(which);
                    moveImageFile(imageUri, targetBucket, position);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    // 実際にファイルを移動させる処理
    private void moveImageFile(Uri sourceUri, Bucket targetBucket, int position) {
        boolean success = false;

        // A. Android 10 (API 29) 以上の場合: MediaStoreの情報を書き換える
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                // 移動先のパスを指定 (例: Pictures/旅行)
                // ※注: 最後に "/" を付けるのがルール
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/" + targetBucket.bucketName + "/");
                // 重要　移動先に同じ名前のファイルがある場合にはIS_PENDINGを使うべきだが，現段階では未実装



                // データベースを更新 (これでファイルも自動的に移動する)
                int rows = getContentResolver().update(sourceUri, values, null, null);

                // 1行でも更新できれば成功
                if (rows > 0) success = true;

            } catch (SecurityException e) {
                // エラーが「許可を求めれば直るもの(Recoverable)」だった場合
                if (e instanceof RecoverableSecurityException) {
                    RecoverableSecurityException recoverable = (RecoverableSecurityException) e;
                    try {
                        // システムの「許可しますか？」ダイアログを出す
                        // (許可されたらもう一度長押しして移動する必要がある)
                        startIntentSenderForResult(
                                recoverable.getUserAction().getActionIntent().getIntentSender(),
                                100, // リクエストコード（適当な数字）
                                null, 0, 0, 0, null);

                        Toast.makeText(this, "許可をしてから、もう一度試してください", Toast.LENGTH_LONG).show();
                        return; // ここで終了
                    } catch (IntentSender.SendIntentException sendEx) {
                        sendEx.printStackTrace();
                    }
                }

                // それ以外のエラー
                Toast.makeText(this, "権限エラー: 移動できませんでした", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            // B. Android 9 (Pie) 以下の場合: 従来の File.renameTo を使う
            String sourcePath = getPathFromUri(sourceUri);
            if (sourcePath != null) {
                File sourceFile = new File(sourcePath);
                File targetDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), targetBucket.bucketName);
                if (!targetDir.exists()) targetDir.mkdirs();
                File destFile = new File(targetDir, sourceFile.getName());

                if (sourceFile.renameTo(destFile)) {
                    success = true;
                    // 古いAndroidの場合はスキャンし直す必要がある
                    MediaScannerConnection.scanFile(this,
                            new String[]{sourcePath, destFile.getAbsolutePath()},
                            null, null);
                }
            }
        }

        // 成功した場合の画面更新処理
        if (success) {
            Toast.makeText(this, "移動しました", Toast.LENGTH_SHORT).show();
            data.remove(position);
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, data.size());
        } else {
            Toast.makeText(this, "移動に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

}
