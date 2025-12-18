package jp.ac.meijou.android.mobileapp_team_b;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class OtherFragment extends Fragment {

    private RecyclerView recyclerView;
    private OtherAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_other, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerOther);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // クリック時の処理(リスナー)を渡すように変更
        adapter = new OtherAdapter(
                requireContext(),
                bucket -> { /* Trashクリック時 */ },
                () -> ((MainActivity) requireActivity()).applyThemeFromManager()
        );
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTrashBucket();
    }

    public void refreshTheme() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (getView() != null) {
            ThemeOption t = ThemeCatalog.getThemes()
                    .get(ThemeManager.getThemeIndex());
            getView().setBackgroundColor(
                    requireContext().getColor(t.appBg)
            );
        }
    }


    private void loadTrashBucket() {
        new Thread(() -> {
            List<Bucket> allList = MediaStoreHelper.queryBuckets(requireContext());

            Bucket trashBucket = null;
            for (Bucket b : allList) {
                if (b.bucketName != null && b.bucketName.equalsIgnoreCase("Trash")) {
                    trashBucket = b;
                    break;
                }
            }

            if (trashBucket == null) {
                trashBucket = new Bucket();
                trashBucket.bucketName = "Trash";
                trashBucket.bucketId = "MANUAL_TRASH";
                trashBucket.count = 0;
                trashBucket.coverUri = null;
            }

            Bucket finalTrashBucket = trashBucket;
            requireActivity().runOnUiThread(() -> adapter.setTrashBucket(finalTrashBucket));
        }).start();
    }
}

