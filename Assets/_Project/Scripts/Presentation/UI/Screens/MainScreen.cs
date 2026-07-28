using UnityEngine;
using UnityEngine.UI;
using Milan.Infrastructure.ServiceLocator;

namespace Milan.Presentation.UI.Screens
{
    public class MainScreen : MonoBehaviour
    {
        [SerializeField] Button gachaButton;
        [SerializeField] Button charactersButton;
        [SerializeField] Button battleButton;
        [SerializeField] Button collectionButton;

        void Start()
        {
            gachaButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("Gacha"));
            charactersButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("CharacterList"));
            battleButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("Battle"));
            collectionButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("Collection"));
        }
    }
}
