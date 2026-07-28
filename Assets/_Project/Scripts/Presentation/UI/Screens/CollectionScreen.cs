using UnityEngine;
using UnityEngine.UI;
using Milan.Infrastructure.ServiceLocator;
using Milan.Services;

namespace Milan.Presentation.UI.Screens
{
    public class CollectionScreen : MonoBehaviour
    {
        [SerializeField] Text collectionText;
        [SerializeField] Button backButton;

        void Start()
        {
            var collection = ServiceLocator.Instance.Get<CollectionService>();
            collectionText.text = "图鉴: " + collection.TotalCharacters();
            backButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("Main"));
        }
    }
}
