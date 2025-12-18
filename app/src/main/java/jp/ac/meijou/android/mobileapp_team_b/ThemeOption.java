package jp.ac.meijou.android.mobileapp_team_b;

public class ThemeOption {
    public final String name;
    public final int appBg;
    public final int titleBg;
    public final int buttonBg;
    public final int buttonStroke;
    public final int bucketBg;
    public final int cardStroke;
    public final int textPrimary;

    public ThemeOption(String name, int appBg, int titleBg, int buttonBg, int buttonStroke,
                       int bucketBg, int cardStroke, int textPrimary) {
        this.name = name;
        this.appBg = appBg;
        this.titleBg = titleBg;
        this.buttonBg = buttonBg;
        this.buttonStroke = buttonStroke;
        this.bucketBg = bucketBg;
        this.cardStroke = cardStroke;
        this.textPrimary = textPrimary;
    }

    @Override public String toString() { return name; } // ← Dropdown表示に使える
}

