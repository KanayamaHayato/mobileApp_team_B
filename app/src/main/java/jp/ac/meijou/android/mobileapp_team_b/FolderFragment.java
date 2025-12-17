package jp.ac.meijou.android.mobileapp_team_b;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jp.ac.meijou.android.mobileapp_team_b.databinding.FragmentFolderBinding;

public class FolderFragment extends Fragment implements Searchable {

    private FragmentFolderBinding binding;

    // 表示用（RecyclerViewが見るリスト）
    private final List<Bucket> data = new ArrayList<>();

    // 検索の元になる全件保持用
    private final List<Bucket> allData = new ArrayList<>();

    private BucketAdapter adapter;
    private String currentQuery = "";

    private String readImagesPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private final ActivityResultLauncher<String> reqReadPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) loadBuckets(); });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFolderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        adapter = new BucketAdapter(requireContext(), data, bucket -> {
            startActivity(new android.content.Intent(requireContext(), PhotoGridActivity.class)
                    .putExtra("bucketId", bucket.bucketId)
                    .putExtra("bucketName", bucket.bucketName));
        });

        binding.recyclerBuckets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBuckets.setAdapter(adapter);

        // ★ ここは削除：ボタンをactivity_mainに置くならFragment側では触らない
        // binding.folderAddButton.setOnClickListener(v -> showCreateFolderDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        ensurePermissionAndLoad();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    public void refreshTheme() {
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void ensurePermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(), readImagesPermission())
                == PackageManager.PERMISSION_GRANTED) {
            loadBuckets();
        } else {
            reqReadPerm.launch(readImagesPermission());
        }
    }

    // ===== 検索 =====
    @Override
    public void onSearchQueryChanged(String query) {
        currentQuery = (query == null) ? "" : query;
        applyFilter();
    }

    private void applyFilter() {
        if (adapter == null) return;

        String q = currentQuery.trim().toLowerCase();

        data.clear();
        if (q.isEmpty()) {
            data.addAll(allData);
        } else {
            for (Bucket b : allData) {
                if (b.bucketName != null && b.bucketName.toLowerCase().contains(q)) {
                    data.add(b);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadBuckets() {
        new Thread(() -> {
            List<Bucket> list = MediaStoreHelper.queryBuckets(requireContext());

            List<Bucket> filteredList = new ArrayList<>();
            for (Bucket b : list) {
                // ★ null安全に
                if (b.bucketName != null && !b.bucketName.equalsIgnoreCase("Trash")) {
                    filteredList.add(b);
                }
            }

            requireActivity().runOnUiThread(() -> {
                allData.clear();
                allData.addAll(filteredList);
                applyFilter();
            });
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ==========================
    // ★ MainActivityから呼ぶ用
    // ==========================
    public void requestCreateFolder() {
        showCreateFolderDialog();
    }

    // フォルダ新規作成ダイアログ
    private void showCreateFolderDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("フォルダ名を入力");

        new AlertDialog.Builder(requireContext())
                .setTitle("新しいフォルダを作成")
                .setView(input)
                .setPositiveButton("作成", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createNewFolder(name);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void createNewFolder(String folderName) {
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File newFolder = new File(picturesDir, folderName);

        // ★ 重複チェックは allData で（検索中でも正しく判定）
        for (Bucket bucket : allData) {
            if (bucket.bucketName != null && bucket.bucketName.equals(folderName)) {
                Toast.makeText(requireContext(), "そのフォルダは既にリストにあります", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (!newFolder.exists()) {
            boolean created = newFolder.mkdirs();
            if (!created) {
                Toast.makeText(requireContext(), "フォルダ作成に失敗しました", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(), "作成しました: " + folderName, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "既存のフォルダを再表示します", Toast.LENGTH_SHORT).show();
        }

        Bucket newBucket = new Bucket();
        newBucket.bucketName = folderName;
        newBucket.bucketId = "MANUAL_" + folderName;
        newBucket.count = 0;
        newBucket.coverUri = null;

        allData.add(0, newBucket);
        applyFilter();

        if (binding != null) binding.recyclerBuckets.scrollToPosition(0);
    }
}

