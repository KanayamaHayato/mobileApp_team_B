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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.ac.meijou.android.mobileapp_team_b.databinding.FragmentFolderBinding;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONException;

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

        // ＋ボタンを押したときの処理
        binding.folderAddButton.setOnClickListener(v -> showCreateFolderDialog());

        // ドラッグ＆ドロップで並び替えをする処理
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, // 上下のドラッグを許可
                0 // 横スワイプ（削除など）は今回は無効(0)にする
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {

                // 1. 移動元の位置と、移動先の位置を取得
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();

                // 2. アダプターに依頼して、見た目とリスト(data)を入れ替える
                adapter.moveItem(fromPos, toPos);

                // 3. (重要) 検索用の元データ(allData)も同期して入れ替えておく
                // これをしないと、検索フィルタを解除したときに順番が戻ってしまいます
                if (currentQuery.isEmpty() && fromPos < allData.size() && toPos < allData.size()) {
                    // 単純な検索なし状態なら、allDataも同じように入れ替える
                    try {
                        Collections.swap(allData, fromPos, toPos);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                return true; // 移動処理完了
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // スワイプ削除は今回実装しないので何もしない
            }

            // ドラッグが始まった瞬間の処理 (拡大する)
            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    // 1.1倍 (10%大きく) にアニメーションさせる
                    viewHolder.itemView.animate().scaleX(1.03f).scaleY(1.1f).setDuration(200).start();

                    // 少し透明度を下げて「浮いている感」を出す
                     viewHolder.itemView.setAlpha(0.9f);
                }
            }

            // ドラッグ操作が終わった（指を離した）ときに保存を実行
            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                // サイズを元の 1.0倍 に戻す
                viewHolder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();

                // 透明度をここでもとに戻す
                 viewHolder.itemView.setAlpha(1.0f);

                // --- 以前追加した保存処理 ---
                if (currentQuery.isEmpty()) {
                    saveFolderOrder();
                }
            }

            // 検索中はドラッグできないようにする（データの不整合を防ぐため）
            @Override
            public boolean isLongPressDragEnabled() {
                return currentQuery.isEmpty();
            }
        });

        // 作成したヘルパーをRecyclerViewに取り付ける
        itemTouchHelper.attachToRecyclerView(binding.recyclerBuckets);


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

            // ここで保存順に並び替える
            sortBucketsBySavedOrder(filteredList);

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

    // ★追加: 現在のリストの並び順を保存する
    private void saveFolderOrder() {
        // フォルダ名のリストを作成
        JSONArray jsonArray = new JSONArray();
        for (Bucket b : allData) {
            jsonArray.put(b.bucketName);
        }

        // SharedPreferencesに保存
        SharedPreferences prefs = requireContext().getSharedPreferences("FolderPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("folder_order", jsonArray.toString()).apply();
    }

    // ★追加: 保存された順番通りにリストを並び替える
    private void sortBucketsBySavedOrder(List<Bucket> buckets) {
        SharedPreferences prefs = requireContext().getSharedPreferences("FolderPrefs", Context.MODE_PRIVATE);
        String jsonString = prefs.getString("folder_order", null);

        if (jsonString == null) return; // 保存データがなければ何もしない

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            List<String> savedOrder = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                savedOrder.add(jsonArray.getString(i));
            }

            // 保存されたリストの順番に基づいて並び替え
            Collections.sort(buckets, (b1, b2) -> {
                int index1 = savedOrder.indexOf(b1.bucketName);
                int index2 = savedOrder.indexOf(b2.bucketName);

                // 両方とも保存リストにある場合、その順序に従う
                if (index1 != -1 && index2 != -1) {
                    return Integer.compare(index1, index2);
                }
                // どちらかがリストにない（新規フォルダなど）場合、リストにない方を先頭(-1)にする
                // (ここはお好みで。index1 == -1 ? 1 : -1 だと末尾に行きます)
                if (index1 == -1 && index2 != -1) return -1;
                if (index1 != -1 && index2 == -1) return 1;

                return 0; // 両方ない場合はそのまま
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}

