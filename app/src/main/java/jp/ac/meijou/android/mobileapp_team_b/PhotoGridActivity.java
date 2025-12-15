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

    // 画像を移動させる際に必要な情報を入れるための変数
    private Uri pendingUri;
    private Bucket pendingTargetBucket;
    private int pendingPosition;
    private static final int REQUEST_PERMISSION_MOVE = 100; // リクエストコード

    private boolean isTrashFolder = false; // ごみ箱フォルダか否か
    private static final int REQUEST_DELETE_ALL = 200; // 削除許可用の番号

    @Override // フォルダ一覧画面（FolderFragment）でタップされたフォルダの情報を受け取る
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_photo_grid);

        String bucketId = getIntent().getStringExtra("bucketId");
        String bucketName = getIntent().getStringExtra("bucketName");
        setTitle(bucketName == null ? "Photos" : bucketName);

        // Trashフォルダかどうかを判定
        if (bucketName != null && bucketName.equalsIgnoreCase("Trash")) {
            isTrashFolder = true;
        }

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

    // 許可画面から戻ってきたときに自動実行されるメソッド
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent IntentData) {
        super.onActivityResult(requestCode, resultCode, IntentData);

        // 「移動の許可リクエスト」から戻ってきて、かつ「OK」だった場合
        if (requestCode == REQUEST_PERMISSION_MOVE && resultCode == RESULT_OK) {
            if (pendingUri != null && pendingTargetBucket != null) {
                // メモしておいた情報を使って、もう一度移動を実行する
                moveImageFile(pendingUri, pendingTargetBucket, pendingPosition);

                // 使い終わったらメモを消す
                pendingUri = null;
                pendingTargetBucket = null;
            }
        }

        // 削除(ゴミ箱を空にする)の許可が降りたとき
        if (requestCode == REQUEST_DELETE_ALL && resultCode == RESULT_OK) {
            // 許可された時点でシステムが削除を実行してくれているため
            // アプリ側は画面のリストを空にするだけでOK
            data.clear();
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "ゴミ箱を空にしました", Toast.LENGTH_SHORT).show();
        }
    }

    // メニューを表示する設定
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Trashフォルダのときだけ、ゴミ箱ボタンを表示する
        if (isTrashFolder) {
            getMenuInflater().inflate(R.menu.menu_trash, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    // ボタンが押されたときの処理
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_empty_trash) {
            // 確認ダイアログを出す
            new AlertDialog.Builder(this)
                    .setTitle("ゴミ箱を空にする")
                    .setMessage("フォルダ内のすべての画像が完全に削除されます。\nよろしいですか？")
                    .setPositiveButton("削除", (dialog, which) -> deleteTotalTrash())
                    .setNegativeButton("キャンセル", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

            // タップしたらFullScreenActivityへ移動するように修正した
            h.itemView.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(v.getContext(), FullScreenActivity.class);
                // "imageUri" という名札をつけて、画像のURIを文字列にして渡す
                intent.putExtra("imageUri", uri.toString());
                v.getContext().startActivity(intent);
            });
            //拡大へ飛ばしたいならここでIntent
//            h.itemView.setOnClickListener(v ->
//                    Toast.makeText(v.getContext(), uri.toString(), Toast.LENGTH_SHORT).show()
//            );

            // 長押し時の処理
            h.itemView.setOnLongClickListener(v -> {
                showActionDialog(uri, pos); // 移動用のダイアログを出す
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


    // 操作を選択するダイアログ (移動 or 削除)
    private void showActionDialog(Uri uri, int position) {
        String[] options = {"フォルダへ移動", "削除 (ごみ箱へ)"};

        new AlertDialog.Builder(this)
                .setTitle("操作を選択")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // 0番目: 「フォルダへ移動」なら、今までのダイアログを出す
                        showMoveDialog(uri, position);
                    } else {
                        // 1番目: 「削除」なら、ごみ箱へ移動させる処理へ
                        confirmTrash(uri, position);
                    }
                })
                .show();
    }

    // 削除前の最終確認
    private void confirmTrash(Uri uri, int position) {
        new AlertDialog.Builder(this)
                .setTitle("削除しますか？")
                .setMessage("画像は「Trash」フォルダに移動されます。")
                .setPositiveButton("削除", (dialog, which) -> {
                    moveToTrash(uri, position);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    // ごみ箱フォルダへ移動させる処理
    private void moveToTrash(Uri uri, int position) {
        // 「Trash」という名前のフォルダデータ(Bucket)をその場で作る
        Bucket trashBucket = new Bucket();
        trashBucket.bucketName = "Trash"; // フォルダ名 (自由に変えてOK)
        trashBucket.bucketId = "MANUAL_TRASH";
        moveImageFile(uri, trashBucket, position);
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
        // ファイル移動が禁止されているため、データベース上の「住所書き換え」を行う
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

                // データベース更新実行 (住所が変わるので実体も自動で移動される)
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
                        // 許可した後に仕えるようメモを残す
                        pendingUri = sourceUri;
                        pendingTargetBucket = targetBucket;
                        pendingPosition = position;

                        // システム標準の「許可しますか？」ポップアップを出す
                        startIntentSenderForResult(
                                recoverable.getUserAction().getActionIntent().getIntentSender(),
                                REQUEST_PERMISSION_MOVE, null, 0, 0, 0, null);

                        // ここで一旦処理を終える（許可をもらえた場合，自動でもう一度このmoveImageFileが実行される）
//                        Toast.makeText(this, "許可をしてから、もう一度試してください", Toast.LENGTH_LONG).show();
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
            // 従来の「ファイルを直接つかんで移動させる」方法で行う
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
            // 「それ以降の順番が変わった」と伝える（これがないと表示がズレる）
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



    // Trashフォルダの中身を全て削除する
    private void deleteTotalTrash() {
        if (data.isEmpty()) {
            Toast.makeText(this, "ゴミ箱は空です", Toast.LENGTH_SHORT).show();
            return;
        }

        // Android 11以上で、かつ「権限を持っていない」場合のみダイアログを出す
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            // 権限がないので、システム標準の削除リクエストを使う（ダイアログが出る）
            android.app.PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), data);
            try {
                startIntentSenderForResult(pi.getIntentSender(), REQUEST_DELETE_ALL, null, 0, 0, 0, null);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        }
        // Android 10以下、または「権限を持っている」場合はこちら（直接削除）
        else {
            // 許可を得ているので、Threadを使ってバックグラウンドで一気に消す
            new Thread(() -> {
                int deleteCount = 0;
                // 削除中にリストを変更しないよう、念のためコピーしたリストで回す
                List<Uri> targetUris = new ArrayList<>(data);

                for (Uri uri : targetUris) {
                    try {
                        // 1件ずつ直接削除 (権限があればダイアログは出ない)
                        getContentResolver().delete(uri, null, null);
                        deleteCount++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // 結果を画面に反映
                int finalCount = deleteCount;
                runOnUiThread(() -> {
                    if (finalCount > 0) {
                        Toast.makeText(this, finalCount + "枚 削除しました", Toast.LENGTH_SHORT).show();
                        data.clear();
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(this, "削除できませんでした", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        }
    }
}
