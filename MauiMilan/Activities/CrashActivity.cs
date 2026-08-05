using Android.App;
using Android.Content;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Widget;

namespace Milan.Maui.Activities;

/// <summary>
/// 崩溃现场展示页。Application 阶段（MauiApp.OnCreate）崩溃时 HomeActivity 可能永远
/// 起不来，普通 AlertDialog 又依赖前台 Activity —— 两者都覆盖不到；
/// 此时由 MauiApp 直接启动本页，把 last_crash.txt / 面包屑摆到用户眼前，
/// 可复制可截图，配合外部目录镜像（/sdcard/Android/data/.../files/crash/）双保险取证。
/// </summary>
[Activity(Theme = "@android:style/Theme.Material.NoActionBar", Exported = false)]
public class CrashActivity : Activity
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);

        var report = Intent?.GetStringExtra("report") ?? "(无)";

        var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
        root.SetBackgroundColor(Color.Argb(255, 16, 16, 22));

        var title = new TextView(this) { Text = "Milan 上次崩溃现场", TextSize = 18 };
        title.SetTextColor(Color.White);
        title.SetPadding(24, 28, 24, 10);
        root.AddView(title);

        var scroll = new ScrollView(this);
        var body = new TextView(this)
        {
            Text = report,
            TextSize = 12,
            Typeface = Typeface.Monospace,
            MovementMethod = new Android.Text.Method.ScrollingMovementMethod(),
        };
        body.SetTextColor(Color.Argb(255, 200, 200, 220));
        body.SetPadding(24, 10, 24, 10);
        scroll.AddView(body);
        root.AddView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, 0, 1f));

        var hint = new TextView(this)
        {
            Text = "提示：这段文字就是闪退原因，点「复制现场」后发回即可定位。",
            TextSize = 12,
        };
        hint.SetTextColor(Color.Argb(255, 140, 140, 160));
        hint.SetPadding(24, 6, 24, 6);
        root.AddView(hint);

        var copy = new Button(this) { Text = "复制现场" };
        copy.Click += (_, _) => Copy(report);
        root.AddView(copy);

        var cont = new Button(this) { Text = "继续进入游戏" };
        cont.Click += (_, _) =>
        {
            try
            {
                var i = new Intent(this, typeof(HomeActivity));
                i.SetFlags(ActivityFlags.NewTask | ActivityFlags.ClearTop);
                StartActivity(i);
            }
            catch { }
            Finish();
        };
        root.AddView(cont);

        SetContentView(root);
    }

    void Copy(string text)
    {
        try
        {
            var cm = (ClipboardManager?)GetSystemService(ClipboardService);
            cm!.PrimaryClip = ClipData.NewPlainText("milan-crash", text);
            Toast.MakeText(this, "已复制到剪贴板", ToastLength.Short)?.Show();
        }
        catch { }
    }
}
