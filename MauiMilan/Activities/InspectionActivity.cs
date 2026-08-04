using Android.App;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui;
using Milan.Maui.Services;

namespace Milan.Maui.Activities;

[Activity(Label = "360°检视")]
public class InspectionActivity : Activity
{
    private OwnedCharacterView _ch = null!;
    private CharacterTurntableView _turntable = null!;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        var id = Intent.GetStringExtra("characterId");
        var ch = GameState.Owned().FirstOrDefault(c => c.Save.CharacterId == id);
        if (ch == null) { Finish(); return; }
        _ch = ch;
        SetContentView(BuildLayout());
    }

    FrameLayout BuildLayout()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var world = AppTheme.World(_ch.World);
        var rarityColor = AppTheme.RarityColor(_ch.Rarity);

        var root = new FrameLayout(this);
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);

        // Dark gradient background（twilight 变体：深紫夜底 + 暮紫夜中）
        var bgGrad = new GradientDrawable();
        bgGrad.SetColors(new[] {
            AppTheme.BgDeepest.ToArgb(),
            AppTheme.BgMid.ToArgb(),
            AppTheme.BgDeepest.ToArgb()
        });
        bgGrad.SetOrientation(GradientDrawable.Orientation.TlBr);
        root.Background = (bgGrad);

        // Subtle particle overlay
        var bg = new TwilightBackground(this);
        bg.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        bg.Alpha = 0.3f;
        root.AddView(bg);
        bg.Start();

        // Main content
        var content = new LinearLayout(this) { Orientation = Orientation.Vertical };
        content.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);

        // Top bar
        var topBar = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        topBar.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        topBar.SetPadding(Dp(16), Dp(40), Dp(16), Dp(8));
        topBar.SetGravity(GravityFlags.CenterVertical);

        var back = UI.Text("‹ 返回", 16, AppTheme.Gold);
        back.Clickable = true; back.Focusable = true;
        back.Click += (s, e) => Finish();
        back.SetPadding(Dp(8), Dp(8), Dp(12), Dp(8));

        var name = UI.Text(_ch.Name, 20, AppTheme.Text1, bold: true);
        name.SetPadding(Dp(8), 0, 0, 0);
        name.SetShadowLayer(6, 0, 2, Color.Argb(120, 0, 0, 0));

        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };

        // Rarity badge
        var rarityBadge = UI.Text(AppTheme.RarityName(_ch.Rarity), 11, rarityColor, bold: true);
        var rBadgeBg = new GradientDrawable();
        rBadgeBg.SetCornerRadius(Dp(10));
        rBadgeBg.SetColor(Color.Argb(50, rarityColor.R, rarityColor.G, rarityColor.B));
        rBadgeBg.SetStroke(1, Color.Argb(180, rarityColor.R, rarityColor.G, rarityColor.B));
        rarityBadge.Background = rBadgeBg;
        rarityBadge.SetPadding(Dp(10), Dp(4), Dp(10), Dp(4));

        topBar.AddView(back);
        topBar.AddView(name);
        topBar.AddView(spacer);
        topBar.AddView(rarityBadge);
        content.AddView(topBar);

        // Portrait area (takes remaining space)
        var portraitArea = new FrameLayout(this);
        var paLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f);
        paLp.SetMargins(0, 0, 0, Dp(8)); // prevent overlap with action bar
        portraitArea.LayoutParameters = paLp;

        // 3D 卡片检视（含厚度、卡背、自动旋转 + 拖拽）
        _turntable = new CharacterTurntableView(this).Bind(_ch);
        _turntable.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        portraitArea.AddView(_turntable);
        content.AddView(portraitArea);

        // Bottom action bar
        var actionBar = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        actionBar.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        actionBar.SetPadding(Dp(12), Dp(8), Dp(12), Dp(16));
        actionBar.SetGravity(GravityFlags.Center);

        var rotBtn = ThemeButtons.Neon(this, "旋 转");
        rotBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        rotBtn.Click += (s, e) => _turntable.ToggleAutoSpin();

        var zoomBtn = ThemeButtons.Neon(this, "缩 放");
        zoomBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        zoomBtn.Click += (s, e) => _turntable.ToggleZoom();

        var skillBtn = ThemeButtons.Gold(this, "技 能");
        skillBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        skillBtn.Click += (s, e) => _turntable.TriggerBurst();

        var infoBtn = ThemeButtons.Neon(this, "信 息");
        infoBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        infoBtn.Click += (s, e) => ShowInfo();

        actionBar.AddView(rotBtn);
        actionBar.AddView(ActionSpacer(Dp(8)));
        actionBar.AddView(zoomBtn);
        actionBar.AddView(ActionSpacer(Dp(8)));
        actionBar.AddView(skillBtn);
        actionBar.AddView(ActionSpacer(Dp(8)));
        actionBar.AddView(infoBtn);

        content.AddView(actionBar);
        root.AddView(content);
        return root;
    }

    void ShowInfo()
    {
        var stats = GameState.ComputeStats(_ch);
        var color = AppTheme.RarityColor(_ch.Rarity);
        var msg = $"{_ch.Name}\n{AppTheme.RarityName(_ch.Rarity)} · {_ch.World} · {_ch.Element}\n" +
                  $"ATK {stats.Atk}  DEF {stats.Def}  HP {stats.Hp}  SPD {stats.Spd}\n\n{_ch.Lore}";
        new AlertDialog.Builder(this)
            .SetTitle("角色信息")
            .SetMessage(msg)
            .SetPositiveButton("确定", (s, e) => { })
            .Show();
    }

    View ActionSpacer(int w) => new View(this) { LayoutParameters = new LinearLayout.LayoutParams(w, 0) };
}
