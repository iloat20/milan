using SkiaSharp;
using SkiaSharp.Views.Maui;
using Milan.Maui.Services;

namespace Milan.Maui.Views;

public partial class GachaPage : ContentPage
{
    private readonly GameService _game;
    private readonly Random _rng = new();

    // 3D 卡牌检视状态
    private float _rotationX;   // 上下翻转
    private float _rotationY;   // 左右旋转
    private float _lastX, _lastY;
    private bool _isDragging;

    // 抽卡动画状态
    private bool _isAnimating;
    private double _animProgress; // 0..1
    private IDispatcherTimer? _animTimer;
    private string _currentCardName = "";
    private int _currentRarity;
    private bool _showCard;

    public GachaPage(GameService game)
    {
        InitializeComponent();
        _game = game;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        CurrencyLabel.Text = $"星尘: {_game.SaveData.SoftCurrency}";
    }

    private void OnBackClicked(object s, EventArgs e) => Navigation.PopAsync();

    // ---- 抽卡逻辑 ----
    private async void OnSinglePullClicked(object s, EventArgs e)
    {
        if (_isAnimating) return;
        var results = _game.Pull("pool_main", false);
        if (results.Count == 0)
        {
            ResultLabel.Text = "货币不足";
            return;
        }
        await StartReveal(results[0]);
    }

    private async void OnTenPullClicked(object s, EventArgs e)
    {
        if (_isAnimating) return;
        var results = _game.Pull("pool_main", true);
        if (results.Count == 0)
        {
            ResultLabel.Text = "货币不足";
            return;
        }
        // 十连：展示最后一个（最佳）结果
        await StartReveal(results.Last());
    }

    private async Task StartReveal(GameService.PullResult result)
    {
        _isAnimating = true;
        SinglePullButton.IsEnabled = false;
        TenPullButton.IsEnabled = false;
        _currentCardName = result.CharacterName;
        _currentRarity = result.Rarity;
        _showCard = false;
        _animProgress = 0;

        // 阶段1：次元裂缝动画（0..0.7）
        _animTimer = Dispatcher.CreateTimer();
        _animTimer.Interval = TimeSpan.FromMilliseconds(16);
        _animTimer.Tick += (s, e) =>
        {
            _animProgress += 0.02;
            CardCanvas.InvalidateSurface();
            if (_animProgress >= 0.7 && !_showCard)
            {
                _showCard = true; // 揭示卡牌
            }
            if (_animProgress >= 1.0)
            {
                _animTimer.Stop();
                _isAnimating = false;
                SinglePullButton.IsEnabled = true;
                TenPullButton.IsEnabled = true;
                ResultLabel.Text = $"获得: {_currentCardName} ({RarityName(_currentRarity)})";
                CurrencyLabel.Text = $"星尘: {_game.SaveData.SoftCurrency}";
            }
        };
        _animTimer.Start();
    }

    private string RarityName(int r) => r switch
    {
        3 => "SSR",
        2 => "SR",
        4 => "UR",
        _ => "R"
    };

    // ---- SkiaSharp 绘制：3D 卡牌 + 抽卡动画 ----
    private void OnCardPaint(object s, SKPaintSurfaceEventArgs e)
    {
        var canvas = e.Surface.Canvas;
        var info = e.Info;
        canvas.Clear(SKColor.Parse("#1a1a2e"));

        float cx = info.Width / 2f;
        float cy = info.Height / 2f;

        if (_isAnimating)
        {
            DrawRitual(canvas, cx, cy, info);
        }
        else if (_showCard)
        {
            DrawCard3D(canvas, cx, cy, info);
        }
        else
        {
            // 默认提示
            using var paint = new SKPaint
            {
                Color = SKColor.Parse("#00d2ff"),
                TextSize = 24,
                IsAntialias = true,
                TextAlign = SKTextAlign.Center
            };
            canvas.DrawText("点击单抽或十连", cx, cy, paint);
        }
    }

    // 次元裂缝抽卡动画
    private void DrawRitual(SKCanvas canvas, float cx, float cy, SKImageInfo info)
    {
        float t = (float)_animProgress;

        // 背景闪光
        float flash = MathF.Sin(t * 20) * 0.3f + 0.3f;
        using var bgPaint = new SKPaint
        {
            Color = new SKColor(233, 69, 96, (byte)(flash * 80))
        };
        canvas.DrawRect(0, 0, info.Width, info.Height, bgPaint);

        // 漩涡
        using var swirlPaint = new SKPaint
        {
            Color = SKColor.Parse("#00d2ff"),
            StrokeWidth = 3,
            IsAntialias = true,
            Style = SKStrokeStyle.Stroke
        };
        for (int i = 0; i < 5; i++)
        {
            float radius = 30 + t * 100 + i * 20;
            float alpha = (byte)Math.Max(0, 255 - (int)(t * 300));
            swirlPaint.Color = new SKColor(0, 210, 255, (byte)Math.Min(255, alpha));
            canvas.DrawCircle(cx, cy, radius, swirlPaint);
        }

        // 光柱（揭示时）
        if (_showCard)
        {
            using var beamPaint = new SKPaint
            {
                Shader = SKShader.CreateRadialGradient(
                    new SKPoint(cx, cy), 120,
                    new SKColor[] { new SKColor(255, 255, 255, 200), new SKColor(233, 69, 96, 0) },
                    null, SKShaderTileMode.Clamp)
            };
            canvas.DrawCircle(cx, cy, 120, beamPaint);
        }
    }

    // 3D 卡牌（带旋转）
    private void DrawCard3D(SKCanvas canvas, float cx, float cy, SKImageInfo info)
    {
        float cardW = Math.Min(info.Width * 0.6f, 280);
        float cardH = cardW * 1.4f;

        canvas.Save();

        // 应用 3D 旋转（用 skew + scale 模拟）
        float scaleY = MathF.Cos(_rotationX * 0.01f);
        float scaleX = MathF.Cos(_rotationY * 0.01f);
        float skewX = _rotationY * 0.005f;

        canvas.Translate(cx, cy);
        canvas.Scale(MathF.Abs(scaleX), MathF.Abs(scaleY));
        canvas.Skew(skewX, 0);
        canvas.Translate(-cx, -cy);

        // 卡牌背景（根据稀有度）
        var (bg1, bg2) = RarityColors(_currentRarity);
        using var cardPaint = new SKPaint
        {
            Shader = SKShader.CreateLinearGradient(
                new SKPoint(cx - cardW / 2, cy - cardH / 2),
                new SKPoint(cx + cardW / 2, cy + cardH / 2),
                new SKColor[] { bg1, bg2 },
                null, SKShaderTileMode.Clamp),
            IsAntialias = true
        };
        var rect = new SKRect(cx - cardW / 2, cy - cardH / 2, cx + cardW / 2, cy + cardH / 2);
        canvas.DrawRoundRect(rect, 16, 16, cardPaint);

        // 卡牌边框
        using var borderPaint = new SKPaint
        {
            Color = SKColor.Parse("#ffffff"),
            StrokeWidth = 3,
            Style = SKStrokeStyle.Stroke,
            IsAntialias = true
        };
        canvas.DrawRoundRect(rect, 16, 16, borderPaint);

        // 角色名
        using var namePaint = new SKPaint
        {
            Color = SKColor.White,
            TextSize = 28,
            IsAntialias = true,
            TextAlign = SKTextAlign.Center,
            Typeface = SKTypeface.FromFamilyName("Arial", SKFontStyle.Bold)
        };
        canvas.DrawText(_currentCardName, cx, cy, namePaint);

        // 稀有度
        using var rarityPaint = new SKPaint
        {
            Color = RarityTextColor(_currentRarity),
            TextSize = 22,
            IsAntialias = true,
            TextAlign = SKTextAlign.Center
        };
        canvas.DrawText(RarityName(_currentRarity), cx, cy + 40, rarityPaint);

        canvas.Restore();

        // 提示文字
        using var hintPaint = new SKPaint
        {
            Color = new SKColor(255, 255, 255, 128),
            TextSize = 14,
            IsAntialias = true,
            TextAlign = SKTextAlign.Center
        };
        canvas.DrawText("拖拽旋转卡牌 · 360° 检视", cx, info.Height - 40, hintPaint);
    }

    private (SKColor, SKColor) RarityColors(int rarity) => rarity switch
    {
        3 => (SKColor.Parse("#e94560"), SKColor.Parse("#ff9a00")), // SSR 金红
        4 => (SKColor.Parse("#9b59b6"), SKColor.Parse("#3498db")), // UR 紫金
        2 => (SKColor.Parse("#00d2ff"), SKColor.Parse("#302b63")), // SR 蓝紫
        _ => (SKColor.Parse("#7f8c8d"), SKColor.Parse("#2c3e50")), // R 灰
    };

    private SKColor RarityTextColor(int rarity) => rarity switch
    {
        3 => SKColor.Parse("#ffd700"),
        4 => SKColor.Parse("#ff00ff"),
        2 => SKColor.Parse("#00ffff"),
        _ => SKColor.Parse("#cccccc"),
    };

    // ---- 触摸旋转 ----
    private void OnCardTouch(object s, SKTouchEventArgs e)
    {
        switch (e.ActionType)
        {
            case SKTouchAction.Pressed:
                _isDragging = true;
                _lastX = e.Location.X;
                _lastY = e.Location.Y;
                break;
            case SKTouchAction.Moved:
                if (_isDragging)
                {
                    _rotationY += (e.Location.X - _lastX) * 0.5f;
                    _rotationX += (e.Location.Y - _lastY) * 0.5f;
                    _rotationX = Math.Clamp(_rotationX, -80, 80);
                    _lastX = e.Location.X;
                    _lastY = e.Location.Y;
                    CardCanvas.InvalidateSurface();
                }
                break;
            case SKTouchAction.Released:
                _isDragging = false;
                break;
        }
        e.Handled = true;
    }
}
