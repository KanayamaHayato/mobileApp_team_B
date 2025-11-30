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
        if (uri.getScheme().equals("content")){ // データベース管理されたデータであることを確認
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
    /**
     * 画像ファイルを指定されたフォルダ(Bucket)へ移動させるメソッド
     * * @param sourceUri    移動元(今ある写真)の場所データ
     * @param targetBucket 移動先(ゴール)のフォルダ情報
     * @param position     画面リスト上の何番目の写真か(移動成功後に画面から消すために必要)
     */
    private void moveImageFile(Uri sourceUri, Bucket targetBucket, int position) {
        // 移動が成功したかどうかを記録するフラグ（最初は false:失敗 にしておく）
        boolean success = false;

        // 元のファイル名を取得 (例: "cat.jpg")
        String originalName = getFileName(sourceUri);
        // 万が一名前が取れなかった場合の保険
        if (originalName == null) originalName = "unknown.jpg";

        // ====================================================================================
        // 【分岐A】 Android 10 (API 29/Q) 以上の新しいスマホの場合
        // 昔ながらのファイル移動が禁止されているため、データベース上の「住所書き換え」を行います。
        // ====================================================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // データベース更新用の入れ物を作成
                ContentValues values = new ContentValues();

                // 移動先のパスを作る (例: "Pictures/旅行/")
                // ※最後に "/" を付けないとフォルダとして認識されないので注意
                String targetPath = "Pictures/" + targetBucket.bucketName + "/";

                // --- ▼ 重複チェック & リネーム処理 (ここから) ▼ ---
                // 移動先に「cat.jpg」があったら「cat (1).jpg」にする処理

                String finalName = originalName; // 最終的に使うファイル名

                // 拡張子(.jpgなど)と、ファイル名本体(cat)を分離する
                String nameNoExt = originalName.contains(".") ? originalName.substring(0, originalName.lastIndexOf('.')) : originalName;
                String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";

                int counter = 1;
                // isPhotoExists: そのフォルダに同じ名前のファイルはあるかをDBに聞く自作メソッド
                // ある(true)と言われたら、カウンターを増やして名前を変えて再挑戦
                while (isPhotoExists(targetPath, finalName)) {
                    finalName = nameNoExt + " (" + counter + ")" + ext; // 例: cat (1).jpg
                    counter++;
                }
                // --- ▲ 重複チェック & リネーム処理 (ここまで) ▲ ---

                // 準備した変更内容をセットする
                // 1. 新しい住所(パス)
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath);

                // 2. 新しい名前 (もしリネームされていたらセット)
                if (!finalName.equals(originalName)) {
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, finalName);
                }

                // 3. 更新日時を「今」にする (整理した順に並びやすくするため)
                values.put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000);

                // ★ データベース更新実行！ (住所が変わるので実体も自動で移動される)
                // update(...) は「変更できた件数」を返すので、1件以上なら成功
                int rows = getContentResolver().update(sourceUri, values, null, null);
                if (rows > 0) success = true;

            } catch (SecurityException e) {
                // 【重要】権限エラーの処理
                // 自分のアプリで撮った写真以外を動かそうとすると、ここでエラーが出る

                if (e instanceof RecoverableSecurityException) {
                    // 「ユーザーに許可をもらえば直るエラー」の場合
                    RecoverableSecurityException recoverable = (RecoverableSecurityException) e;
                    try {
                        // システム標準の「許可しますか？」ポップアップを出す
                        startIntentSenderForResult(
                                recoverable.getUserAction().getActionIntent().getIntentSender(),
                                100, null, 0, 0, 0, null);

                        // ここで一旦処理を終える（許可をもらった後、ユーザーにもう一度操作してもらう）
                        Toast.makeText(this, "許可をしてから、もう一度試してください", Toast.LENGTH_LONG).show();
                        return;
                    } catch (IntentSender.SendIntentException sendEx) {
                        sendEx.printStackTrace();
                    }
                }

                // どうにもならないエラーの場合
                e.printStackTrace();
                Toast.makeText(this, "権限エラー: 移動できませんでした", Toast.LENGTH_SHORT).show();
                return;
            } catch (Exception e) {
                // その他の予期せぬエラー
                e.printStackTrace();
            }

        } else {
            // ====================================================================================
            // 【分岐B】 Android 9 (API 28/Pie) 以下の古いスマホの場合
            // 従来の「ファイルを直接つかんで移動させる」方法で行います。
            // ====================================================================================

            // URIから「/storage/emulated/0/...」のような実際のパスを取得
            String sourcePath = getPathFromUri(sourceUri);

            if (sourcePath != null) {
                File sourceFile = new File(sourcePath); // 移動元のファイル

                // 移動先のフォルダ場所 (例: Pictures/旅行)
                File targetDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), targetBucket.bucketName);

                // 移動先フォルダが物理的に無ければ作る(mkdirs)
                if (!targetDir.exists()) targetDir.mkdirs();

                // --- ▼ 重複チェック & リネーム処理 (ここから) ▼ ---
                // 移動先フォルダの中に、同じ名前のファイルが「実際に存在するか」チェック

                File destFile = new File(targetDir, sourceFile.getName()); // とりあえずそのままの名前で予定地作成

                String nameNoExt = sourceFile.getName().contains(".") ? sourceFile.getName().substring(0, sourceFile.getName().lastIndexOf('.')) : sourceFile.getName();
                String ext = sourceFile.getName().contains(".") ? sourceFile.getName().substring(sourceFile.getName().lastIndexOf('.')) : "";

                int counter = 1;
                // exists(): 物理的にファイルがあるか確認
                while (destFile.exists()) {
                    String newName = nameNoExt + " (" + counter + ")" + ext;
                    destFile = new File(targetDir, newName); // 名前を変えて再チェック
                    counter++;
                }
                // --- ▲ 重複チェック & リネーム処理 (ここまで) ▲ ---

                // ★ ファイル移動実行！ (renameTo)
                // 成功すると true が返ってくる
                if (sourceFile.renameTo(destFile)) {
                    success = true;

                    // 【重要】古いAndroidでは、ファイルを勝手に動かしてもギャラリーアプリが気づかない。
                    // 「ここにあったファイルはあっちに行ったよ！」と教えてあげる(スキャン)必要がある。
                    MediaScannerConnection.scanFile(this,
                            new String[]{sourcePath, destFile.getAbsolutePath()}, // [古い場所, 新しい場所]
                            null, null);
                }
            }
        }

        // ====================================================================================
        // 共通の終了処理
        // ====================================================================================
        if (success) {
            // 成功したら画面を更新して、移動したような演出をする
            Toast.makeText(this, "移動しました", Toast.LENGTH_SHORT).show();

            // 画面のリストデータから削除
            data.remove(position);
            // アダプター(画面管理係)に「この場所のが消えた」と伝える
            adapter.notifyItemRemoved(position);
            // 「それ以降の順番が変わったよ」と伝える（これがないと表示がズレる）
            adapter.notifyItemRangeChanged(position, data.size());
        } else {
            // 失敗したらメッセージだけ出す
            Toast.makeText(this, "移動に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }


    // Android 10以上のスマホで、データベースの中に同じ名前のファイルがあるか確認するメソッド
    // 指定されたフォルダ(パス)に、指定された名前の画像がすでに存在するかをデータベースに問い合わせる
    private boolean isPhotoExists(String targetPath, String fileName) {
        // 画像のデータベースを見る
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        // 検索条件: 「パスが一致」かつ「名前が一致」するもの
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] args = new String[]{ targetPath, fileName };

        try (Cursor c = getContentResolver().query(collection, new String[]{MediaStore.MediaColumns._ID}, selection, args, null)) {
            // 見つかった数(count)が 0より大きければ「存在する(true)」
            return c != null && c.getCount() > 0;
        } catch (Exception e) {
            // エラーが起きたらとりあえず「ない」ことにする
            return false;
        }
    }

}
