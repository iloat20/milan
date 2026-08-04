using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Views;
using Android.Widget;
using Milan.Maui;

namespace Milan.Maui.Activities;

[Activity(Label = "设置", ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize)]
public class SettingsActivity : Activity
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        var root = Build();
        SetContentView(root);
        root.Post(() => Motion.Fade(root, Motion.Trans));
    }

    public override void OnConfigurationChanged(Android.Content.Res.Configuration newConfig)
    {
        base.OnConfigurationChanged(newConfig);
        var root = Build();
        SetContentView(root);
        root.Post(() => Motion.Fade(root, Motion.Trans));
    }

    FrameLayout Build()
    {
        var root = new FrameLayout(this);
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.Background = (new GradientDrawable(
            GradientDrawable.Orientation.TlBr,
            new[] { AppTheme.BgDeepest.ToArgb(), Color.ParseColor("#0E0A1E").ToArgb(), AppTheme.BgDeepest.ToArgb() }));

        var main = UI.VBox();
        main.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        main.SetPadding(UI.Dp(0), UI.Dp(38), UI.Dp(0), 0);

        main.AddView(AppChrome.AppTopBar(this, "设置", () => Finish()));

        var scroll = new ScrollView(this)
        {
            LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f)
        };
        var inner = UI.VBox();
        inner.SetPadding(UI.Dp(16), UI.Dp(10), UI.Dp(16), UI.Dp(10));

        inner.AddView(BuildGroup("账户",
            Row("用户 ID", UI.Text(string.IsNullOrEmpty(GameState.Service.SaveData.UserId) ? "未登录" : GameState.Service.SaveData.UserId, 13, AppTheme.Text2)),
            Row("云同步", UI.Text(GameState.Service.SaveData.ServerSyncStatus == 1 ? "已同步" : "未同步", 13, AppTheme.Frost, bold: true))
        ));

        inner.AddView(Spacer(16));
        // A2：设置项从 SaveData 读取初值并回写，重启不再丢失。SaveData 即 _save.Current 同一引用。
        var save = GameState.Service.SaveData;
        var sound = new GoldToggle(this) { IsChecked = save.SoundEnabled };
        sound.CheckedChanged += b =>
        {
            save.SoundEnabled = b;
            GameState.Service.Save();
            Toast(b ? "音效已开启" : "音效已关闭");
        };
        var vib = new GoldToggle(this) { IsChecked = save.VibrationEnabled };
        vib.CheckedChanged += b =>
        {
            save.VibrationEnabled = b;
            GameState.Service.Save();
            Toast(b ? "振动反馈已开启" : "振动反馈已关闭");
        };
        var push = new GoldToggle(this) { IsChecked = save.PushEnabled };
        push.CheckedChanged += b =>
        {
            save.PushEnabled = b;
            GameState.Service.Save();
            Toast(b ? "推送通知已开启" : "推送通知已关闭");
        };
        inner.AddView(BuildGroup("偏好",
            Row("音效", sound),
            Row("振动反馈", vib),
            Row("推送通知", push),
            Row("画质", UI.Text("高", 13, AppTheme.Gold, bold: true))
        ));

        inner.AddView(Spacer(16));
        var ver = UI.Text("1.0.0", 13, AppTheme.Text2);
        var privacy = ThemeButtons.Neon(this, "查看", 13);
        privacy.Click += (_, _) => Toast("隐私政策（示例）");
        inner.AddView(BuildGroup("关于",
            Row("版本", ver),
            Row("隐私政策", privacy)
        ));

        scroll.AddView(inner);
        main.AddView(scroll);

        var nav = new GameNavBar(this, GameNavBar.NavItem.Settings, OnNav);
        nav.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        main.AddView(nav);

        root.AddView(main);
        return root;
    }

    LinearLayout BuildGroup(string title, params LinearLayout[] rows)
    {
        var g = UI.VBox();
        g.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        g.Background = UI.GlassPanel(14, gold: false);
        g.SetPadding(UI.Dp(6), UI.Dp(6), UI.Dp(6), UI.Dp(6));

        var head = UI.TitleWithOrnament(title, 16);
        head.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        var hLp = (LinearLayout.LayoutParams)head.LayoutParameters;
        hLp.SetMargins(UI.Dp(8), UI.Dp(6), UI.Dp(8), UI.Dp(6));
        g.AddView(head);

        foreach (var r in rows) g.AddView(r);
        return g;
    }

    LinearLayout Row(string label, View control)
    {
        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        var rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        rowLp.SetMargins(UI.Dp(6), UI.Dp(4), UI.Dp(6), UI.Dp(4));
        row.LayoutParameters = rowLp;
        row.Background = UI.GlassPanel(10, gold: false, nested: true);
        row.SetPadding(UI.Dp(14), UI.Dp(13), UI.Dp(14), UI.Dp(13));
        row.SetGravity(GravityFlags.CenterVertical);

        row.AddView(UI.Text(label, 14, AppTheme.Text1));
        row.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) });
        row.AddView(control);
        return row;
    }

    void OnNav(GameNavBar.NavItem item)
    {
        var target = item switch
        {
            GameNavBar.NavItem.Home => typeof(HomeActivity),
            GameNavBar.NavItem.Gacha => typeof(GachaActivity),
            GameNavBar.NavItem.Deck => typeof(DeckActivity),
            GameNavBar.NavItem.Shop => typeof(ShopActivity),
            GameNavBar.NavItem.Settings => typeof(SettingsActivity),
            _ => null
        };
        Nav.To(this, target);
    }

    void Toast(string msg) => Android.Widget.Toast.MakeText(this, msg, Android.Widget.ToastLength.Short)?.Show();

    View Spacer(int h)
    {
        var d = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * d)) };
    }
}

/// <summary>金色自定义开关（Obsidian &amp; Gold）：开=金色轨道+亮金钮，关=灰轨道+白钮。</summary>
public sealed class GoldToggle : FrameLayout
{
    private bool _on;
    private readonly Paint _paint = new() { AntiAlias = true };

    public event System.Action<bool>? CheckedChanged;

    public GoldToggle(Context context) : base(context) => Init();

    private void Init()
    {
        Clickable = true; Focusable = true;
        this.Touch += (s, e) =>
        {
            if (e?.Event?.Action == MotionEventActions.Up) Toggle();
            if (e != null) e.Handled = true;
        };
    }

    public bool IsChecked { get => _on; set { _on = value; Invalidate(); } }

    public void Toggle() { _on = !_on; Invalidate(); CheckedChanged?.Invoke(_on); }

    protected override void OnMeasure(int wms, int hms)
    {
        int w = UI.Dp(46), h = UI.Dp(26);
        SetMeasuredDimension(ResolveSize(w, wms), ResolveSize(h, hms));
    }

    protected override void OnDraw(Canvas canvas)
    {
        float w = Width, h = Height;
        if (w == 0 || h == 0) return;
        float r = h / 2f;

        var track = new GradientDrawable(GradientDrawable.Orientation.LeftRight,
            new[] { (_on ? AppTheme.GoldDeep : Color.Argb(55, 255, 255, 255)).ToArgb(),
                    (_on ? AppTheme.Gold : Color.Argb(45, 255, 255, 255)).ToArgb() });
        track.SetCornerRadius(r);
        track.SetBounds(0, 0, (int)w, (int)h);
        track.Draw(canvas);

        float pad = UI.Dp(3);
        float tr = h / 2f - pad;
        float cx = _on ? w - tr - pad : tr + pad;
        _paint.SetStyle(Paint.Style.Fill);
        _paint.Color = _on ? AppTheme.GoldHi : Color.Argb(235, 220, 220, 230);
        _paint.SetShadowLayer(6, 0, 1, Color.Argb(120, 0, 0, 0));
        canvas.DrawCircle(cx, h / 2f, tr, _paint);
        _paint.ClearShadowLayer();
    }

}
