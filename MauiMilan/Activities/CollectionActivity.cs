using Android.App;
using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Text;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui;
using Milan.Maui.Services;
using System.Collections.Generic;

namespace Milan.Maui.Activities;

[Activity(Label = "图鉴")]
public class CollectionActivity : Activity
{
    LinearLayout _gridRoot = null!;
    ScrollView _scroll = null!;
    ListFilterBar? _filter;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        var rootView = Build();
        SetContentView(rootView);
        rootView.Post(() => PlayEntrance(rootView));
    }

    void PlayEntrance(ViewGroup root)
    {
        try { Motion.Fade(root, Motion.Trans); }
        catch (System.Exception) { }
    }

    protected override void OnResume()
    {
        base.OnResume();
        // Rebuild grid to reflect current ownership
        ApplyAndRebuild();
    }

    LinearLayout Build()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.SetPadding(Dp(16), Dp(40), Dp(16), Dp(16));

        // Twilight 深色渐变背景
        var bgGrad = new GradientDrawable();
        bgGrad.SetColors(new[] {
            AppTheme.BgDeepest.ToArgb(),
            AppTheme.BgMid.ToArgb(),
            AppTheme.BgDeepest.ToArgb()
        });
        bgGrad.SetOrientation(GradientDrawable.Orientation.TlBr);
        root.Background = (bgGrad);

        // ═══ TOP BAR ═══
        root.AddView(AppChrome.AppTopBar(this, "角 色 图 鉴", Finish));
        root.AddView(Spacer(10));

        // Progress bar
        var progressBox = UI.VBox();
        progressBox.Background = UI.RoundRect(AppTheme.Surface, 12);
        progressBox.SetPadding(Dp(14), Dp(10), Dp(14), Dp(10));
        var owned = GameState.OwnedCount;
        var total = Math.Max(1, GameState.Service.Characters.Count); // 动态取全角色数，避免与内容脱节
        var progress = (float)owned / total;
        var progressLabel = UI.Text($"收集进度  {owned} / {total}", 12, AppTheme.Text2);

        // Progress bar track
        var track = new FrameLayout(this);
        var trackLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(8));
        trackLp.SetMargins(0, Dp(6), 0, 0);
        track.LayoutParameters = trackLp;
        var trackBg = new GradientDrawable();
        trackBg.SetCornerRadius(Dp(4));
        trackBg.SetColor(Color.Argb(40, 255, 255, 255));
        track.Background = trackBg;

        // Progress bar fill（金色）
        var fill = new View(this);
        var fillBg = new GradientDrawable();
        fillBg.SetCornerRadius(Dp(4));
        fillBg.SetColors(new[] { AppTheme.GoldDeep.ToArgb(), AppTheme.GoldHi.ToArgb() });
        fill.Background = fillBg;
        var fillWidth = (int)(140 * progress);
        var fillLp = new FrameLayout.LayoutParams(Math.Max(fillWidth, Dp(8)), Dp(8));
        fill.LayoutParameters = fillLp;

        progressBox.AddView(progressLabel);
        track.AddView(fill);
        progressBox.AddView(track);
        root.AddView(progressBox);
        root.AddView(Spacer(12));

        var hint = new TextView(this) { Text = "点击立绘查看详情" };
        hint.SetTextColor(AppTheme.Text3);
        hint.SetTextSize(ComplexUnitType.Sp, 12);
        hint.SetPadding(Dp(4), 0, 0, Dp(8));
        root.AddView(hint);
        root.AddView(Spacer(8));

        // ═══ 筛选条 ═══
        _filter = new ListFilterBar(this, GameState.Service.Characters.Select(c => c.Element).Distinct());
        _filter.Changed += () => ApplyAndRebuild();
        root.AddView(_filter.View);
        root.AddView(Spacer(8));

        // ═══ GRID ═══
        _scroll = new ScrollView(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f) };
        _gridRoot = new LinearLayout(this) { Orientation = Orientation.Vertical };
        _scroll.AddView(_gridRoot);
        root.AddView(_scroll);

        ApplyAndRebuild();
        return root;
    }

    /// <summary>
    /// 按当前筛选条件重建网格，并保留滚动位置（A1：OnResume 全量重建不再丢失滚动）。
    /// </summary>
    void ApplyAndRebuild()
    {
        if (_gridRoot == null) return;

        var ownedIds = new HashSet<string>(GameState.Owned().Select(o => o.Save.CharacterId));
        var chars = _filter == null
            ? GameState.Service.Characters
            : _filter.FilterSort(GameState.Service.Characters,
                getName: c => c.DisplayName,
                getRarity: c => c.BaseRarity,
                getElement: c => c.Element,
                getGroupKey: c => c.World);

        int savedY = _scroll.ScrollY;
        _gridRoot.RemoveAllViews();

        int perRow = 3;
        LinearLayout? row = null;
        for (int i = 0; i < chars.Count; i++)
        {
            if (i % perRow == 0)
            {
                row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
                row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
                _gridRoot.AddView(row);
            }
            row!.AddView(PortraitCell(chars[i], ownedIds.Contains(chars[i].CharacterId)));
        }

        _scroll.Post(() => _scroll.ScrollTo(0, savedY));
    }

    View PortraitCell(CharacterDataEntry ch, bool owned)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var (from, to, glow, _) = ElementTheme.For(ch.Element);
        var rarityCol = AppTheme.RarityColor(ch.BaseRarity); // 统一取自 AppTheme，避免多处配色漂移
        var world = AppTheme.World(ch.World);

        var cell = new LinearLayout(this) { Orientation = Orientation.Vertical };
        var lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        lp.SetMargins(Dp(5), Dp(5), Dp(5), Dp(5));
        cell.LayoutParameters = lp;

        cell.SetPadding(Dp(6), Dp(6), Dp(6), Dp(6));
        cell.Background = AppTheme.WorldCard(world.Primary, AppTheme.Surface, 14);
        cell.Focusable = true; cell.Clickable = true;
        cell.Click += (_, _) => OpenDetail(ch);

        // Portrait（未拥有 → 冷调暗覆盖剪影）
        var portraitFrame = new FrameLayout(this);
        var portraitLp = new LinearLayout.LayoutParams(Dp(100), Dp(140));
        portraitFrame.LayoutParameters = portraitLp;
        var portrait = new PortraitView(this).Bind(ch);
        portrait.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        portraitFrame.AddView(portrait);
        if (!owned)
        {
            var cover = new View(this);
            cover.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
            cover.SetBackgroundColor(Color.Argb(150, 10, 8, 20));
            portraitFrame.AddView(cover);
        }
        cell.AddView(portraitFrame);

        // Name
        var name = new TextView(this) { Text = ch.DisplayName };
        name.SetTextColor(AppTheme.Text1);
        name.SetTextSize(ComplexUnitType.Sp, 12);
        name.SetTypeface(null, TypefaceStyle.Bold);
        name.Gravity = GravityFlags.CenterHorizontal;
        name.SetMaxLines(1); name.Ellipsize = TextUtils.TruncateAt.End;
        name.SetPadding(0, Dp(4), 0, 0);
        cell.AddView(name);

        // Rarity stars
        var stars = new TextView(this) { Text = new string('★', ch.BaseRarity) };
        stars.SetTextColor(rarityCol);
        stars.SetTextSize(ComplexUnitType.Sp, 10);
        stars.Gravity = GravityFlags.CenterHorizontal;
        cell.AddView(stars);

        return cell;
    }

    void OpenDetail(CharacterDataEntry ch)
    {
        var intent = new Intent(this, typeof(CharacterDetailActivity));
        intent.PutExtra("characterId", ch.CharacterId);
        StartActivity(intent);
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }
}
