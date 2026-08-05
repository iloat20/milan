using System;
using System.Collections.Generic;

namespace Milan.Infrastructure.EventBus
{
    /// <summary>
    /// 轻量事件总线。Publish 只入队，必须由宿主定期调用 <see cref="Dispatch"/> 才会真正派发。
    /// #16: 所有静态状态的读写都在 _gate 下进行，handler 调用放在锁外，避免 handler 内再订阅造成死锁。
    /// </summary>
    public static class EventBus
    {
        static readonly object _gate = new();
        static readonly Dictionary<Type, Delegate> _subs = new();
        static readonly Queue<(Type t, object e)> _q = new();

        /// <summary>队列上限，防止无人调用 Dispatch 时事件无限堆积（#17）。</summary>
        const int MaxQueued = 512;

        public static void Subscribe<T>(Action<T> h) where T : struct
        {
            if (h == null) return;
            lock (_gate)
            {
                if (_subs.TryGetValue(typeof(T), out var e) && e != null)
                {
                    // #36: 先移除同一个委托再合并，保证重复 Subscribe 不会被调用多次。
                    var dedup = Delegate.Remove(e, h);
                    _subs[typeof(T)] = dedup == null ? h : Delegate.Combine(dedup, h)!;
                }
                else
                {
                    _subs[typeof(T)] = h;
                }
            }
        }

        public static void Unsubscribe<T>(Action<T> h) where T : struct
        {
            if (h == null) return;
            lock (_gate)
            {
                if (_subs.TryGetValue(typeof(T), out var e) && e != null)
                {
                    var r = Delegate.Remove(e, h);
                    if (r == null) _subs.Remove(typeof(T)); else _subs[typeof(T)] = r;
                }
            }
        }

        public static void Publish<T>(T e) where T : struct
        {
            lock (_gate)
            {
                // #17: 无人 Dispatch 时丢弃最旧事件，避免队列无界增长。
                while (_q.Count >= MaxQueued) _q.Dequeue();
                _q.Enqueue((typeof(T), e));
            }
        }

        /// <summary>当前待派发事件数（供宿主判断是否需要 Dispatch）。</summary>
        public static int PendingCount { get { lock (_gate) { return _q.Count; } } }

        public static void Dispatch()
        {
            // 只消费本轮开始时已入队的事件：handler 内再 Publish 的新事件留到下一轮，
            // 否则 handler 内 Publish 同类型事件会造成无限循环。
            int n;
            lock (_gate) { n = _q.Count; }
            if (n <= 0) return;

            for (int i = 0; i < n; i++)
            {
                Type t;
                object e;
                Delegate? h;
                lock (_gate)
                {
                    if (_q.Count == 0) break;
                    (t, e) = _q.Dequeue();
                    _subs.TryGetValue(t, out h);
                }
                if (h == null) continue;

                // 锁外调用：handler 内可安全地 Subscribe / Unsubscribe / Publish。
                foreach (var handler in h.GetInvocationList())
                {
                    try { handler.DynamicInvoke(e); }
                    catch (Exception ex)
                    {
                        // One failing handler must not break the multicast chain or lose later events.
                        System.Diagnostics.Debug.WriteLine($"[Milan] EventBus handler threw for {t.Name}: {ex}");
                    }
                }
            }
        }

        public static void Clear() { lock (_gate) { _subs.Clear(); _q.Clear(); } }

        // I-6: 仅清空队列（场景卸载时调用），保留订阅者
        public static void ClearQueue() { lock (_gate) { _q.Clear(); } }

        // I-6: 取消某个目标对象的所有订阅（在 Activity.OnDestroy 中调用）
        public static void UnsubscribeAll(object target)
        {
            if (target == null) return;
            lock (_gate)
            {
                var types = new List<Type>(_subs.Keys);
                foreach (var t in types)
                {
                    if (_subs.TryGetValue(t, out var del) && del != null)
                    {
                        var removed = del;
                        foreach (var handler in del.GetInvocationList())
                        {
                            if (ReferenceEquals(handler.Target, target))
                                removed = Delegate.Remove(removed, handler)!;
                        }
                        if (removed == null) _subs.Remove(t);
                        else if (!ReferenceEquals(removed, del)) _subs[t] = removed;
                    }
                }
            }
        }
    }
}
