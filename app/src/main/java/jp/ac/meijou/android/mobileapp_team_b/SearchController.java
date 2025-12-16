package jp.ac.meijou.android.mobileapp_team_b;

public class SearchController {

    private Searchable target;

    public void setTarget(Searchable target) {
        this.target = target;
    }

    public void onQueryChanged(String query) {
        if (target != null) {
            target.onSearchQueryChanged(query);
        }
    }
}

