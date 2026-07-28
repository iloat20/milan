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
                if (_subs.TryGetValue(t, out var h)) h.DynamicInvoke(e);
            }
        }
        public static void Clear() { _subs.Clear(); _q.Clear(); }
    }
}
