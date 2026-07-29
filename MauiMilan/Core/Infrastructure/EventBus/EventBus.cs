using System;
using System.Collections.Generic;

namespace Milan.Infrastructure.EventBus
{
    public static class EventBus
    {
        static readonly Dictionary<Type, Delegate> _subs = new();
        static readonly Queue<(Type t, object e)> _q = new();

        public static void Subscribe<T>(Action<T> h) where T : struct
        {
            if (_subs.TryGetValue(typeof(T), out var e)) _subs[typeof(T)] = Delegate.Combine(e, h);
            else _subs[typeof(T)] = h;
        }
        public static void Unsubscribe<T>(Action<T> h) where T : struct
        {
            if (_subs.TryGetValue(typeof(T), out var e))
            {
                var r = Delegate.Remove(e, h);
                if (r == null) _subs.Remove(typeof(T)); else _subs[typeof(T)] = r;
            }
        }
        public static void Publish<T>(T e) where T : struct => _q.Enqueue((typeof(T), e));
        public static void Dispatch()
        {
            while (_q.Count > 0)
            {
                var (t, e) = _q.Dequeue();
                if (!_subs.TryGetValue(t, out var h)) continue;
                foreach (var handler in h.GetInvocationList())
                {
                    try { handler.DynamicInvoke(e); }
                    catch (System.Exception ex)
                    {
                        // One failing handler must not break the multicast chain or lose later events.
                        System.Diagnostics.Debug.WriteLine($"[Milan] EventBus handler threw for {t.Name}: {ex}");
                    }
                }
            }
        }
        public static void Clear() { _subs.Clear(); _q.Clear(); }

        // I-6: 仅清空队列（场景卸载时调用），保留订阅者
        public static void ClearQueue() { _q.Clear(); }

        // I-6: 取消某个目标对象的所有订阅（在 MonoBehaviour.OnDestroy 中调用）
        public static void UnsubscribeAll(object target)
        {
            if (target == null) return;
            var types = new List<Type>(_subs.Keys);
            foreach (var t in types)
            {
                if (_subs.TryGetValue(t, out var del) && del != null)
                {
                    var removed = del;
                    foreach (var handler in del.GetInvocationList())
                    {
                        if (handler.Target == target)
                            removed = Delegate.Remove(removed, handler);
                    }
                    if (removed == null) _subs.Remove(t);
                    else if (!ReferenceEquals(removed, del)) _subs[t] = removed;
                }
            }
        }
    }
}
