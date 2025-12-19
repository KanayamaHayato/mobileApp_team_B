package jp.ac.meijou.android.mobileapp_team_b;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import jp.ac.meijou.android.mobileapp_team_b.databinding.ActivityAiTestBinding;

public class AiTest extends AppCompatActivity {

    private ActivityAiTestBinding binding;
    private ImageClassifier imageClassifier;
    private ConnectivityManager connectivityManager;
    private List<String> labels; // ラベル一覧を保持する変数

    // 今判定に使う画像を保持
    private Bitmap selectedBitmap;

    // ギャラリー用ランチャー（onCreate で初期化する）
    private ActivityResultLauncher<Intent> pickImageLauncher;

    // 判定されたラベル名を保持しておく変数
    private String detectedLabelName = null;
    // 2回押し防止用のフラグ
    private boolean isSaved = false;

    // 選択中の画像のURI（元々どのフォルダにいたか調べる用）
    private Uri currentUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityAiTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        //テーマ切り替えのやつ
        applyTheme();


        // ==== ギャラリーランチャーの初期化（super のあと！） ====
        pickImageLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                                Uri uri = result.getData().getData();
                                if (uri != null) {
                                    try {
                                        // 選んだ画像のURIを保持しておく
                                        currentUri = uri;

                                        InputStream inputStream = getContentResolver().openInputStream(uri);
                                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                        if (inputStream != null) {
                                            inputStream.close();
                                        }
                                        // 選んだ画像を保持＆表示
                                        selectedBitmap = bitmap;
                                        binding.AISampleImageView.setImageBitmap(selectedBitmap);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        Toast.makeText(this, "画像の読み込みに失敗しました", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                        }
                );

        // ラベル読み込み
        loadLabels();

        // EfficientNet モデルをロード
        try {
            imageClassifier = ImageClassifier.createFromFile(this, "efficientnet-lite0-int8.tflite");
        } catch (IOException e) {
            e.printStackTrace();
        }
        // ★ MainActivity から画像が送られてきていないかチェック
        byte[] bytes = getIntent().getByteArrayExtra("captured_image");
        if (bytes != null) {
            // カメラで撮った画像がある場合
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            selectedBitmap = bitmap;
            binding.AISampleImageView.setImageBitmap(selectedBitmap);

            // ここでカメラ画像であることを表示しておく
            binding.AISampleResultText.setText("カメラで撮影した画像で判定します");

            // 自動でAI判定する
            classifyImage();
        } else {
            // 何も渡されていないときは、従来通り sample5 を使う
            selectedBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sample5);
            binding.AISampleImageView.setImageBitmap(selectedBitmap);

            binding.AISampleResultText.setText("サンプル画像で判定します");
        }

        // 「AI判定」ボタン → 今の画像を判定
        binding.AISampleButton.setOnClickListener(view -> {
            // ちゃんとここに来てるかチェック
            binding.AISampleResultText.setText("ボタン押された！");
            classifyImage();
        });

        // 「画像選択」ボタン → ギャラリーを開く
        binding.ImagePickButton.setOnClickListener(view -> openGallery());


        // 判定された画像をフォルダに移動するボタンの動作
        binding.addFolderButton.setOnClickListener(view -> {
            if (selectedBitmap != null && detectedLabelName != null) {

                // 対策1: すでに保存済みなら処理しない
                if (isSaved) {
                    Toast.makeText(this, "すでに移動しています", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 対策2: 移動先フォルダと現在のフォルダが同じなら保存しない
                String targetFolderName = sanitizeFolderName(detectedLabelName); // AIが出したフォルダ名

                if (currentUri != null) {
                    // 元画像のフォルダ名を取得
                    String currentFolderName = getBucketNameFromUri(currentUri);

                    if (currentFolderName != null && currentFolderName.equalsIgnoreCase(targetFolderName)) {
                        Toast.makeText(this, "すでに「" + targetFolderName + "」フォルダにあります", Toast.LENGTH_SHORT).show();
                        isSaved = true; // これ以上押せないように保存済みにする
                        return;
                    }
                }

                // 問題なければ保存実行
                saveImageToFolder(selectedBitmap, detectedLabelName);

                // ★追加: 保存完了フラグを立てる
                isSaved = true;
                binding.addFolderButton.setText("移動完了"); // ボタンの文字を変えるとより親切

            } else {
                Toast.makeText(this, "先にAI判定を行ってください", Toast.LENGTH_SHORT).show();
            }
        });


        // システムバー分だけパディング
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    // ラベルファイルを読み込む
    private void loadLabels() {
        labels = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open("labels.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ギャラリーを開く
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    // 判定結果のリセット処理
    private void resetResult() {
        detectedLabelName = null;
        binding.AISampleResultText.setText("判定ボタンを押してください");
        binding.addFolderButton.setEnabled(false); // ボタンを押せないようにする

        // リセット時は保存フラグを下ろす
        isSaved = false;
        binding.addFolderButton.setText("判定されたフォルダへ移動");
    }


    // 今選択されている画像を判定
    private void classifyImage() {

        if (imageClassifier == null) {
            binding.AISampleResultText.setText("モデルの読み込みに失敗しています");
            return;
        }

        if (selectedBitmap == null) {
            binding.AISampleResultText.setText("画像が選択されていません");
            return;
        }

        try {
            // Bitmap を TensorImage に変換
            TensorImage image = TensorImage.fromBitmap(selectedBitmap);

            // 推論実行
            List<Classifications> results = imageClassifier.classify(image);

            if (results == null || results.isEmpty()) {
                binding.AISampleResultText.setText("予測できませんでした（結果なし）");
                return;
            }

            List<Category> categories = results.get(0).getCategories();
            if (categories == null || categories.isEmpty()) {
                binding.AISampleResultText.setText("分類結果なし");
                return;
            }

            Category topResult = categories.get(0);
            int labelIndex = topResult.getIndex();  // クラスID
            float score = topResult.getScore();     // 信頼度 (0〜1)

            // ★ ラベルの範囲チェックを必ず入れる
            String labelName;
            if (labelIndex >= 0 && labelIndex < labels.size()) {
                labelName = labels.get(labelIndex);
            } else {
                labelName = "Unknown(" + labelIndex + ")";
            }

            // 判定結果を変数に保存し、ボタンを有効化する
            detectedLabelName = labelName; // 例: "cat"

            // 再判定したら保存ボタン復活」とする
            isSaved = false;
            binding.addFolderButton.setText("判定されたフォルダへ移動");
            binding.addFolderButton.setEnabled(true); // 保存ボタンを押せるようにする

            String resultText = "予測: " + labelIndex + " , " + labelName
                    + "\n信頼度: " + String.format("%.2f", (score * 100)) + " %";
            binding.AISampleResultText.setText(resultText);

        } catch (Exception e) {
            e.printStackTrace();
            binding.AISampleResultText.setText("エラー: " + e.getClass().getSimpleName());
        }
    }

    // フォルダ名用の文字列整形（記号削除など）を共通化
    private String sanitizeFolderName(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9_\\- ]", "");
        return safe.isEmpty() ? "Other" : safe;
    }

    // 画像のURIから「フォルダ名(Bucket Display Name)」を取得するメソッド
    private String getBucketNameFromUri(Uri uri) {
        String bucketName = null;
        String[] projection = {MediaStore.Images.Media.BUCKET_DISPLAY_NAME};

        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                // 列インデックスを取得して文字列を取り出す
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                bucketName = cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bucketName;
    }

    // 画像を指定したフォルダ名のフォルダに保存するメソッド
    private void saveImageToFolder(Bitmap bitmap, String folderName) {
        // フォルダ名に使えない文字などを除去（念のため）
        // 共通メソッドを使う
        String safeFolderName = sanitizeFolderName(folderName);

        // 保存するファイル名を作成 (例: cat_123456789.jpg)
        String fileName = safeFolderName + "_" + System.currentTimeMillis() + ".jpg";

        // 保存場所の設定
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        // Android 10以上: RELATIVE_PATH でフォルダを指定（自動作成される）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + safeFolderName);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        } else {
            // Android 9以下: 従来のパス指定（今回は省略するが、通常はFile操作が必要）
        }

        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri itemUri = getContentResolver().insert(collection, values);

        if (itemUri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(itemUri)) {
                // 画像を書き込む
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

                // 書き込み完了設定 (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(itemUri, values, null, null);
                }

                Toast.makeText(this, "「" + safeFolderName + "」フォルダに保存しました", Toast.LENGTH_LONG).show();

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "保存に失敗しました", Toast.LENGTH_SHORT).show();
            }
        }
    }
    //AItest用のテーマ切り替えクラス

    private void applyTheme() {
        ThemeOption t = ThemeCatalog.getThemes()
                .get(ThemeManager.getThemeIndex());

        // 背景色
        int bg = ContextCompat.getColor(this, t.appBg);

        // ボタン色
        int btn = ContextCompat.getColor(this, t.buttonBg);
        int stroke = ContextCompat.getColor(this, t.buttonStroke);//これはふちのこと
        //テキスト色
        int text = ContextCompat.getColor(this, t.textPrimary);

        // === 画面全体の背景 ===
        findViewById(R.id.main).setBackgroundColor(bg);
        binding.AISampleResultText.setTextColor(text);//画面上部の文字の色

        // === ボタン背景＋フチ ===
        binding.AISampleButton.setBackgroundTintList(ColorStateList.valueOf(btn));
        binding.AISampleButton.setStrokeColor(ColorStateList.valueOf(stroke));
        binding.AISampleButton.setTextColor(Color.WHITE);//回りくどいけどこれで無理やり文字を白くします

        binding.ImagePickButton.setBackgroundTintList(ColorStateList.valueOf(btn));
        binding.ImagePickButton.setStrokeColor(ColorStateList.valueOf(stroke));
        binding.ImagePickButton.setTextColor(Color.WHITE);

        binding.addFolderButton.setBackgroundTintList(ColorStateList.valueOf(btn));
        binding.addFolderButton.setStrokeColor(ColorStateList.valueOf(stroke));
        binding.addFolderButton.setTextColor(Color.WHITE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyTheme();
    }
    //-ここまでテーマ切り替え用

}