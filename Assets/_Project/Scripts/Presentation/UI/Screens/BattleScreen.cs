using UnityEngine;
using UnityEngine.UI;
using Milan.Infrastructure.ServiceLocator;
using Milan.Services;

namespace Milan.Presentation.UI.Screens
{
    public class BattleScreen : MonoBehaviour
    {
        [SerializeField] Button startButton;
        [SerializeField] Text resultText;
        [SerializeField] Button backButton;

        void Start()
        {
            var battle = ServiceLocator.Instance.Get<BattleService>();
            startButton.onClick.AddListener(() =>
            {
                var result = battle.RunStage("stage_1");  // I-3: 使用玩家已培养的队伍
                resultText.text = result.Victory ? "胜利!" : "失败";
            });
            backButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("Main"));
        }
    }
}
