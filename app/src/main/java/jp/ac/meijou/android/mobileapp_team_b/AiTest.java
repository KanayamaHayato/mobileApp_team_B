package jp.ac.meijou.android.mobileapp_team_b;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityAiTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ==== ギャラリーランチャーの初期化（super のあと！） ====
        pickImageLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                                Uri uri = result.getData().getData();
                                if (uri != null) {
                                    try {
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

        // 最初は sample5 を判定対象にしておく
        selectedBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sample5);
        binding.AISampleImageView.setImageBitmap(selectedBitmap);

        // 「AI判定」ボタン → 今の画像を判定
        binding.AISampleButton.setOnClickListener(view -> classifyImage());

        // 「画像選択」ボタン → ギャラリーを開く
        binding.ImagePickButton.setOnClickListener(view -> openGallery());

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

    // 今選択されている画像を判定
    private void classifyImage() {
        if (selectedBitmap == null) {
            Toast.makeText(this, "画像が選択されていません", Toast.LENGTH_SHORT).show();
            return;
        }

        // Bitmap を TensorImage に変換
        TensorImage image = TensorImage.fromBitmap(selectedBitmap);

        // 推論実行
        List<Classifications> results = imageClassifier.classify(image);

        // 結果を画面に表示
        if (results != null && !results.isEmpty()) {
            List<Category> categories = results.get(0).getCategories();
            if (categories != null && !categories.isEmpty()) {
                Category topResult = categories.get(0);
                int labelIndex = topResult.getIndex();      // クラスID
                String labelName = labels.get(labelIndex);  // ラベル名
                float score = topResult.getScore();         // 信頼度 (0〜1)

                String resultText = "予測: " + labelIndex + " , " + labelName
                        + "\n信頼度: " + String.format("%.2f", (score * 100)) + " %";
                binding.AISampleResultText.setText(resultText);
            } else {
                binding.AISampleResultText.setText("分類結果なし");
            }
        } else {
            binding.AISampleResultText.setText("予測できませんでした");
        }
    }
}
