using UnityEngine;
using UnityEngine.UI;
using Milan.Infrastructure.ServiceLocator;
using Milan.Services;
using Milan.Domain.Battle;

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
                var team = new[] { new UnitStats { Atk = 100, Hp = 1000, Spd = 10 } };
                var result = battle.RunStage("stage_1", team);
                resultText.text = result.Victory ? "胜利!" : "失败";
            });
            backButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("Main"));
        }
    }
}
