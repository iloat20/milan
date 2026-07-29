using Android.App;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Widget;
using Milan.Domain.Battle;
using Milan.Maui;

namespace Milan.Maui.Activities;

[Activity(Label = "检视")]
public class InspectionActivity : Activity
{
    private OwnedCharacterView _ch = null!;
    private FullBodyCharacter _portrait = null!;
    private float _lastX;
    private float _rotationY;

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

        var root = new FrameLayout(this);
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);

        var bg = new CosmicBackground(this);
        bg.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.AddView(bg);
        bg.Start();

        var content = new LinearLayout(this) { Orientation = Orientation.Vertical };
        content.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        content.SetPadding(Dp(16), Dp(36), Dp(16), Dp(12));

        // Top bar
        var top = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        var back = UI.Text("‹ 返回", 16, AppTheme.CosmicPrimarySoft);
        back.Clickable = true; back.Focusable = true;
        back.Click += (s, e) => Finish();
        var name = UI.Text(_ch.Name, 20, AppTheme.CosmicTextPrimary, bold: true);
        name.SetPadding(Dp(12), 0, 0, 0);
        top.AddView(back); top.AddView(name);
        content.AddView(top);
        content.AddView(Spacer(8));

        // Portrait area (touch to rotate)
        var portraitArea = new FrameLayout(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f) };
        _portrait = new FullBodyCharacter(this, _ch.Def!);
        var portLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        _portrait.LayoutParameters = portLp;
        portraitArea.AddView(_portrait);

        // Touch rotation
        portraitArea.SetOnTouchListener(new TouchListener((e) =>
        {
            switch (e.Action)
            {
                case MotionEventActions.Down:
                    _lastX = e.GetX();
                    break;
                case MotionEventActions.Move:
                    var dx = e.GetX() - _lastX;
                    _rotationY += dx * 0.5f;
                    _portrait.RotationY = _rotationY;
                    _lastX = e.GetX();
                    break;
            }
        }));
        content.AddView(portraitArea);
        content.AddView(Spacer(8));

        // Action buttons
        var actions = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        actions.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        
        var rotBtn = new GlowButton(this, "旋 转", AppTheme.CosmicPrimary, Color.White, 10);
        rotBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        rotBtn.Click += (s, e) => _portrait.Animate().RotationYBy(45).SetDuration(300).Start();
        var zoomBtn = new GlowButton(this, "缩 放", AppTheme.CosmicPrimary, Color.White, 10);
        zoomBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        zoomBtn.Click += (s, e) => _portrait.Animate().ScaleX(1.2f).ScaleY(1.2f).SetDuration(200)
            .Start();
        var skillBtn = new GlowButton(this, "技 能", Color.ParseColor("#ff6b00"), Color.White, 10);
        skillBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        skillBtn.Click += (s, e) => _portrait.TriggerBurst();
        var infoBtn = new GlowButton(this, "信 息", AppTheme.CosmicPrimary, Color.White, 10);
        infoBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        infoBtn.Click += (s, e) => ShowInfo();
        actions.AddView(rotBtn); actions.AddView(SpacerH(Dp(6)));
        actions.AddView(zoomBtn); actions.AddView(SpacerH(Dp(6)));
        actions.AddView(skillBtn); actions.AddView(SpacerH(Dp(6)));
        actions.AddView(infoBtn);
        content.AddView(actions);

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

    View Spacer(int h) => new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * Resources.DisplayMetrics.Density)) };
    View SpacerH(int w) => new View(this) { LayoutParameters = new LinearLayout.LayoutParams((int)(w * Resources.DisplayMetrics.Density), 0) };
}

class TouchListener : Java.Lang.Object, View.IOnTouchListener
{
    private readonly System.Action<MotionEvent> _handler;
    public TouchListener(System.Action<MotionEvent> handler) { _handler = handler; }
    public bool OnTouch(View v, MotionEvent e) { _handler(e); return true; }
}
