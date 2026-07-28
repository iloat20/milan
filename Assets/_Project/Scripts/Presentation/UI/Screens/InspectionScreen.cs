using UnityEngine;
using UnityEngine.UI;
using Milan.Infrastructure.ServiceLocator;

namespace Milan.Presentation.UI.Screens
{
    public class InspectionScreen : MonoBehaviour
    {
        [SerializeField] Button backButton;
        [SerializeField] Text infoText;

        void Start()
        {
            infoText.text = "3D 检视 (占位)";
            backButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("Main"));
        }
    }
}
