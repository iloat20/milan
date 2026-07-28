using UnityEngine;
using UnityEngine.UI;
using Milan.Infrastructure.ServiceLocator;
using Milan.Services;

namespace Milan.Presentation.UI.Screens
{
    public class GachaScreen : MonoBehaviour
    {
        [SerializeField] Button pullSingleButton;
        [SerializeField] Button pullTenButton;
        [SerializeField] Button backButton;
        [SerializeField] Text resultText;

        void Start()
        {
            var gacha = ServiceLocator.Instance.Get<GachaService>();
            pullSingleButton.onClick.AddListener(() =>
            {
                var pools = gacha.GetAllPools();
                if (pools.Length == 0) { resultText.text = "No pools"; return; }
                var id = gacha.Pull(pools[0], false);
                resultText.text = id != null ? "获得: " + id : "货币不足";
            });
            pullTenButton.onClick.AddListener(() =>
            {
                var pools = gacha.GetAllPools();
                if (pools.Length == 0) { resultText.text = "No pools"; return; }
                var id = gacha.Pull(pools[0], true);
                resultText.text = id != null ? "十连获得: " + id : "货币不足";
            });
            backButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("Main"));
        }
    }
}
