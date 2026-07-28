using UnityEngine;
using UnityEngine.SceneManagement;
using Milan.Infrastructure.Save;
using Milan.Infrastructure.ServiceLocator;
using Milan.Services;

namespace Milan.Presentation
{
    public class Bootstrap : MonoBehaviour
    {
        void Awake()
        {
            var go = new GameObject("ServiceLocator");
            DontDestroyOnLoad(go);
            var locator = go.AddComponent<ServiceLocator>();
            ServiceLocator.Instance = locator;

            var save = new SaveManager(new LocalSaveProvider());
            save.Load();

            locator.Register(save);
            locator.Register(new GachaService(save));
            locator.Register(new ProgressionService(save));
            locator.Register(new BattleService(save));
            locator.Register(new CollectionService(save));

            // I-6: 场景卸载时清理本场景发布中的事件，避免分发到已销毁对象
            SceneManager.sceneUnloaded += OnSceneUnloaded;
        }

        void OnSceneUnloaded(Scene scene) => Infrastructure.EventBus.EventBus.ClearQueue();

        void OnDestroy()
        {
            SceneManager.sceneUnloaded -= OnSceneUnloaded;
        }
    }
}
