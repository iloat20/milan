using Android.Runtime;
using System.Threading.Tasks;

namespace Milan.Maui;

/// <summary>
/// 崩溃取证。设备无法连 adb 抓 logcat 时，用「落盘 + 下次启动回显」的方式拿到现场。
///
/// 两条线索：
///  1. last_crash.txt —— 托管层未捕获异常的完整堆栈（含 Java 侧 Throwable）。
///  2. boot_trace.txt —— 启动阶段面包屑。每次启动清空后逐步追加，
///     若进程是 native 崩溃（SIGSEGV / AOT 问题），托管异常处理器不会触发，
///     但面包屑会停在最后一个到达的阶段 —— 这正是区分「托管异常」和「native 崩溃」的判据。
/// </summary>
public static class CrashReporter
{
    static string BaseDir => System.Environment.GetFolderPath(System.Environment.SpecialFolder.Personal);
    static string CrashPath => Path.Combine(BaseDir, "last_crash.txt");
    static string TracePath => Path.Combine(BaseDir, "boot_trace.txt");

    /// <summary>
    /// 外部镜像目录：/sdcard/Android/data/&lt;pkg&gt;/files/crash/。
    /// 内部目录（/data/data/...）在无 root / 无 adb 时用户取不到；
    /// 镜像一份到外部后，用 MT管理器 / 文件管理器 / 电脑 MTP 即可取回现场。
    /// 每次访问实时求值（MauiApp.Context 在 OnCreate 早期赋值），失败静默跳过。
    /// </summary>
    static string? ExternalDir
    {
        get
        {
            try
            {
                var ctx = MauiApp.Context;
                if (ctx == null) return null;
                var d = ctx.GetExternalFilesDir(null);
                return d == null ? null : Path.Combine(d.AbsolutePath, "crash");
            }
            catch { return null; }
        }
    }

    /// <summary>把取证文件整体镜像到外部目录（调用方需持有 _gate 锁）。
    /// 外部目录 IO 较重，改为后台线程落盘，避免阻塞 UI 线程 / 启动路径（P5）。</summary>
    static void Mirror(string name, string content)
    {
        try
        {
            var d = ExternalDir;
            if (d == null) return;
            var full = Path.Combine(d, name);
            // 捕获不可变局部变量后交线程池，落盘全程不持有 _gate 锁。
            Task.Run(() =>
            {
                try { Directory.CreateDirectory(d); File.WriteAllText(full, content); }
                catch { }
            });
        }
        catch { }
    }

    /// <summary>把单行增量追加进外部镜像（调用方需持有 _gate 锁）。用于高频 Boot 路径，避免每次读回全量 trace。后台落盘（P5）。</summary>
    static void MirrorAppend(string name, string line)
    {
        try
        {
            var d = ExternalDir;
            if (d == null) return;
            var full = Path.Combine(d, name);
            Task.Run(() =>
            {
                try { Directory.CreateDirectory(d); File.AppendAllText(full, line); }
                catch { }
            });
        }
        catch { }
    }

    static readonly object _gate = new();
    static bool _installed;

    /// <summary>安装全局异常钩子。必须在 Application.OnCreate 最开始调用。</summary>
    public static void Install()
    {
        if (_installed) return;
        _installed = true;

        AndroidEnvironment.UnhandledExceptionRaiser += (_, e) =>
        {
            Write("AndroidEnvironment.UnhandledExceptionRaiser", e.Exception);
            // 不设 e.Handled —— 让它照常崩，避免应用停在不一致状态。
        };

        AppDomain.CurrentDomain.UnhandledException += (_, e) =>
            Write("AppDomain.UnhandledException", e.ExceptionObject as Exception);

        TaskScheduler.UnobservedTaskException += (_, e) =>
        {
            Write("TaskScheduler.UnobservedTaskException", e.Exception);
            e.SetObserved();
        };
    }

    /// <summary>启动面包屑。阶段名要短且唯一。</summary>
    public static void Boot(string stage)
    {
        try
        {
            lock (_gate)
            {
                var line = $"{DateTime.Now:HH:mm:ss.fff}  {stage}\n";
                // 轮转保护：单轮启动面包屑过长时停止追加，避免无限增长与反复读全文件造成 O(n^2) IO（#20/#21）。
                if (new FileInfo(TracePath).Length < 65536)
                    File.AppendAllText(TracePath, line);
                // 外部镜像改为增量追加单行，不再每次读回全量 trace。
                MirrorAppend("boot_trace.txt", line);
            }
        }
        catch { /* 取证代码本身绝不能再抛 */ }
    }

    /// <summary>新一轮启动：把上一轮的面包屑归档为 prev_boot_trace，然后清空。</summary>
    public static void BeginBootTrace()
    {
        try
        {
            lock (_gate)
            {
                if (File.Exists(TracePath))
                {
                    var prev = File.ReadAllText(TracePath);
                    File.Copy(TracePath, Path.Combine(BaseDir, "prev_boot_trace.txt"), overwrite: true);
                    Mirror("prev_boot_trace.txt", prev);
                }
                var header = $"=== boot {DateTime.Now:yyyy-MM-dd HH:mm:ss} ===\n";
                File.WriteAllText(TracePath, header);
                Mirror("boot_trace.txt", header);
            }
        }
        catch { }
    }

    public static string Write(string source, Exception? ex)
    {
        string text;
        try
        {
            var sb = new System.Text.StringBuilder();
            sb.AppendLine($"时间: {DateTime.Now:yyyy-MM-dd HH:mm:ss}");
            sb.AppendLine($"来源: {source}");
            sb.AppendLine($"机型: {Android.OS.Build.Manufacturer} {Android.OS.Build.Model}");
            sb.AppendLine($"系统: Android {Android.OS.Build.VERSION.Release} (API {(int)Android.OS.Build.VERSION.SdkInt})");
            sb.AppendLine($"ABI : {string.Join(",", Android.OS.Build.SupportedAbis ?? new List<string>())}");
            sb.AppendLine();

            var e = ex;
            int depth = 0;
            while (e != null && depth++ < 6)
            {
                sb.AppendLine($"[{e.GetType().FullName}] {e.Message}");
                sb.AppendLine(e.StackTrace ?? "(无堆栈)");
            if (e is Java.Lang.Throwable jt)
            {
                // 注意：string + StackTraceElement[] 走 object 拼接只会得到 "Java.Lang.StackTraceElement[]"，
                // 关键线索 100% 丢失。必须逐帧拼成可读字符串（#3）。
                var st = jt.GetStackTrace();
                sb.AppendLine("Java 堆栈:");
                if (st != null)
                    foreach (var frame in st)
                        sb.AppendLine("  at " + frame);
                else
                    sb.AppendLine("  (无)");
            }
                e = e.InnerException;
                if (e != null) sb.AppendLine("--- InnerException ---");
            }

            sb.AppendLine();
            sb.AppendLine("--- 本次启动面包屑 ---");
            try { sb.AppendLine(File.ReadAllText(TracePath)); } catch { sb.AppendLine("(无)"); }

            text = sb.ToString();
            lock (_gate)
            {
                File.WriteAllText(CrashPath, text);
                Mirror("last_crash.txt", text);
            }
        }
        catch
        {
            text = $"{source}: {ex?.Message ?? "unknown"}";
            // 主路径抛异常时至少把最小线索落盘，避免崩溃现场完全蒸发（#22）。
            try { lock (_gate) { File.WriteAllText(CrashPath, text); Mirror("last_crash.txt", text); } } catch { }
        }
        TryShowDialog(text);
        return text;
    }

    /// <summary>
    /// 若当前有前台 Activity，尽力弹出崩溃现场对话框。进程可能即将死亡，故仅尽力而为；
    /// 即使弹不出来，落盘的 last_crash.txt 仍保证下次启动（HomeActivity）回显。
    /// </summary>
    static void TryShowDialog(string report)
    {
        try
        {
            if (MauiApp.Current is not Android.App.Activity act) return;
            act.RunOnUiThread(() =>
            {
                try
                {
                    var shown = report.Length > 3000 ? report.Substring(0, 3000) : report;
                    new Android.App.AlertDialog.Builder(act)
                        .SetTitle("应用崩溃 · 现场已记录")
                        .SetMessage(shown)
                        .SetPositiveButton("复制", (_, _) =>
                        {
                            try
                            {
                                var cm = (Android.Content.ClipboardManager?)act.GetSystemService(Android.Content.Context.ClipboardService);
                                cm!.PrimaryClip = Android.Content.ClipData.NewPlainText("milan-crash", report);
                            }
                            catch { }
                        })
                        .SetNegativeButton("退出", (_, _) => act.Finish())
                        .SetCancelable(false)
                        .Show();
                }
                catch { }
            });
        }
        catch { }
    }

    /// <summary>读取上一次崩溃报告（不删除），供「先展示、后清除」流程使用（#4）。</summary>
    public static string? PeekCrash()
    {
        try { lock (_gate) { return File.Exists(CrashPath) ? File.ReadAllText(CrashPath) : null; } }
        catch { return null; }
    }

    /// <summary>删除已落盘的崩溃报告（内部 + 外部镜像）。</summary>
    public static void ClearCrash()
    {
        try
        {
            lock (_gate)
            {
                if (File.Exists(CrashPath)) File.Delete(CrashPath);
                try
                {
                    var d = ExternalDir;
                    if (d != null)
                    {
                        var ep = Path.Combine(d, "last_crash.txt");
                        if (File.Exists(ep)) File.Delete(ep);
                    }
                }
                catch { }
            }
        }
        catch { }
    }

    /// <summary>读取上一次崩溃报告（读完即删，避免反复弹窗）。HomeActivity 兜底回显使用。</summary>
    public static string? ReadAndClear()
    {
        var text = PeekCrash();
        if (text != null) ClearCrash();
        return text;
    }

    /// <summary>删除「上一轮启动」归档标记。Application 阶段已展示过现场时调用，
    /// 防止 HomeActivity 兜底回显把同一份现场弹两次。</summary>
    public static void ClearPreviousBootFlag()
    {
        try
        {
            lock (_gate)
            {
                var p = Path.Combine(BaseDir, "prev_boot_trace.txt");
                if (File.Exists(p)) File.Delete(p);
                try
                {
                    var d = ExternalDir;
                    if (d != null)
                    {
                        var ep = Path.Combine(d, "prev_boot_trace.txt");
                        if (File.Exists(ep)) File.Delete(ep);
                    }
                }
                catch { }
            }
        }
        catch { }
    }

    /// <summary>上一次启动的面包屑 —— native 崩溃时唯一的线索。</summary>
    public static string? PreviousBootTrace()
    {
        try
        {
            var p = Path.Combine(BaseDir, "prev_boot_trace.txt");
            return File.Exists(p) ? File.ReadAllText(p) : null;
        }
        catch { return null; }
    }

    /// <summary>上一轮启动是否走完了全流程。没走完 = 上次是崩溃退出。</summary>
    public static bool PreviousBootIncomplete()
    {
        var t = PreviousBootTrace();
        return !string.IsNullOrEmpty(t) && !t.Contains("home.oncreate.done");
    }
}
