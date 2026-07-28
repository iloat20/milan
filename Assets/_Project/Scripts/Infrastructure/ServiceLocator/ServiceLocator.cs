using System.Collections.Generic;
using UnityEngine;

namespace Milan.Infrastructure.ServiceLocator
{
    public class ServiceLocator : MonoBehaviour
    {
        public static ServiceLocator Instance { get; private set; }
        readonly Dictionary<System.Type, object> _services = new();

        void Awake()
        {
            if (Instance != null) { Destroy(gameObject); return; }
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }

        public void Register<T>(T service) where T : class
        {
            _services[typeof(T)] = service;
        }

        public T Get<T>() where T : class
        {
            if (_services.TryGetValue(typeof(T), out var s)) return s as T;
            return null;
        }
    }
}
