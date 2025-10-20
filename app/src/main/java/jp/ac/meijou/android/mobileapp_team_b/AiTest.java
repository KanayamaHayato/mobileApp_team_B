package jp.ac.meijou.android.mobileapp_team_b;

import android.net.ConnectivityManager;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;

import jp.ac.meijou.android.mobileapp_team_b.databinding.ActivityAiTestBinding;
import jp.ac.meijou.android.mobileapp_team_b.databinding.ActivityMainBinding;

public class AiTest extends AppCompatActivity {

    private ActivityAiTestBinding binding;
    private ImageClassifier imageClassifier;
    private ConnectivityManager connectivityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityAiTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        // EfficientNet モデルをロード
        try {
            imageClassifier = ImageClassifier.createFromFile(this, "efficientnet-lite0-int8.tflite");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // ボタンのクリック処理
        binding.AISampleButton.setOnClickListener(view -> {
            classifyImage();
        });


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}