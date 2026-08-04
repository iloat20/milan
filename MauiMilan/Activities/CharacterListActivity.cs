using Android.App;
using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Text;
using Android.Views;
using Android.Widget;
using Milan.Maui;

namespace Milan.Maui.Activities;

[Activity(Label = "角色")]
public class CharacterListActivity : Activity
{
    LinearLayout _gridRoot = null!;
    ScrollView _scroll = null!;
    TextView _countLabel = null!;
    ListFilterBar? _filter;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        var rootView = BuildLayout();
        SetContentView(rootView);
        ApplyAndRebuild();
        rootView.Post(() => PlayEntrance(rootView));
    }

    void PlayEntrance(ViewGroup root)
    {
        try
        {
            var decel = new Android.Views.Animations.DecelerateInterpolator();
            root.Alpha = 0f;
            root.Animate()?.Alpha(1f)?.SetDuration(320)?.SetInterpolator(decel)?.Start();
            if (_gridRoot != null)
            {
                for (int i = 0; i < _gridRoot.ChildCount; i++)
                {
                    var c = _gridRoot.GetChildAt(i);
                    if (c == null) continue;
                    c.Alpha = 0f;
                    c.TranslationY = UI.Dp(14);
                    c.Animate()?.Alpha(1f)?.TranslationY(0)?.SetDuration(380)?.SetStartDelay(140 + i * 60)?.SetInterpolator(decel)?.Start();
                }
            }
        }
        catch (System.Exception) { }
    }

    protected override void OnResume()
    {
        base.OnResume();
        ApplyAndRebuild();
    }

    LinearLayout BuildLayout()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.SetPadding(Dp(18), Dp(40), Dp(18), Dp(18));

        // Obsidian 深色渐变背景
        var bgGrad = new GradientDrawable();
        bgGrad.SetColors(new[] {
            AppTheme.BgDeepest.ToArgb(),
            AppTheme.BgMid.ToArgb(),
            AppTheme.BgDeepest.ToArgb()
        });
        bgGrad.SetOrientation(GradientDrawable.Orientation.TlBr);
        root.Background = (bgGrad);

        // ═══ TOP BAR ═══
        root.AddView(AppChrome.AppTopBar(this, "我 的 角 色", Finish));
        root.AddView(Spacer(14));

        // 数量（筛选后实时更新）
        _countLabel = UI.Text("", 14, AppTheme.Text2);
        root.AddView(_countLabel);
        root.AddView(Spacer(12));

        // ═══ 筛选条 ═══
        _filter = new ListFilterBar(this, GameState.Owned().Select(o => o.Element).Distinct());
        _filter.Changed += () => ApplyAndRebuild();
        root.AddView(_filter.View);
        root.AddView(Spacer(8));

        // ═══ GRID ═══
        _scroll = new ScrollView(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f) };
        _gridRoot = UI.VBox();
        _scroll.AddView(_gridRoot);
        root.AddView(_scroll);

        return root;
    }

    /// <summary>
    /// 按当前筛选条件重建网格，并保留滚动位置（A1：OnResume 全量重建不再丢失滚动）。
    /// 重建前记录 ScrollY，重建后 Post 回滚，避免键盘/筛选抖动。
    /// </summary>
    void ApplyAndRebuild()
    {
        if (_gridRoot == null) return;

        var owned = _filter == null
            ? GameState.Owned()
            : _filter.FilterSort(GameState.Owned(),
                getName: o => o.Name,
                getRarity: o => o.Rarity,
                getElement: o => o.Element,
                getGroupKey: o => o.World);

        int total = GameState.OwnedCount;
        _countLabel.Text = owned.Count == total
            ? $"已拥有  {total}  位角色"
            : $"已显示  {owned.Count} / 已拥有 {total}  位角色";

        int savedY = _scroll.ScrollY;
        _gridRoot.RemoveAllViews();

        if (owned.Count == 0)
        {
            var empty = UI.Text("没有符合条件的角色", 14, AppTheme.Text3);
            empty.Gravity = GravityFlags.Center;
            empty.SetPadding(0, 80, 0, 0);
            _gridRoot.AddView(empty);
            return;
        }

        // 2 columns: each row holds two cards
        LinearLayout? row = null;
        for (int i = 0; i < owned.Count; i++)
        {
            if (i % 2 == 0)
            {
                row = UI.HBox();
                row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
                _gridRoot.AddView(row);
            }
            var ch = owned[i];
            row!.AddView(CharacterCard.ListCard(this, ch, () => OpenDetail(ch)));
        }

        // 回滚滚动位置（布局完成后）
        _scroll.Post(() => _scroll.ScrollTo(0, savedY));
    }

    void OpenDetail(OwnedCharacterView ch)
    {
        var intent = new Intent(this, typeof(CharacterDetailActivity));
        intent.PutExtra("characterId", ch.Save.CharacterId);
        StartActivity(intent);
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }
}
