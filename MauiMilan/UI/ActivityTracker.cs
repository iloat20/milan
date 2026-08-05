using Android.App;
using Android.OS;

namespace Milan.Maui;

/// <summary>
/// 追踪当前前台 Activity 并写入 <see cref="MauiApp.Current"/>，
/// 供 CrashReporter 在未捕获异常（含子页面 OnCreate、自绘 View 的 OnDraw）时弹出现场对话框。
/// 这样无需给每个 Activity 单独加 try/catch 也能定位任意页面的崩溃。
/// </summary>
public class ActivityTracker : Java.Lang.Object, Application.IActivityLifecycleCallbacks
{
    public void OnActivityCreated(Activity activity, Bundle? savedInstanceState) => MauiApp.Current = activity;
    public void OnActivityResumed(Activity activity) => MauiApp.Current = activity;
    public void OnActivityPaused(Activity activity) { if (MauiApp.Current == activity) MauiApp.Current = null; }
    public void OnActivityDestroyed(Activity activity) { if (MauiApp.Current == activity) MauiApp.Current = null; }
    public void OnActivityStarted(Activity activity) { }
    public void OnActivityStopped(Activity activity) { }
    public void OnActivitySaveInstanceState(Activity activity, Bundle outState) { }
}
