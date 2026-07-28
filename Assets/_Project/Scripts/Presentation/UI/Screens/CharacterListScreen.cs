using UnityEngine;
using UnityEngine.UI;
using Milan.Infrastructure.ServiceLocator;
using Milan.Services;

namespace Milan.Presentation.UI.Screens
{
    public class CharacterListScreen : MonoBehaviour
    {
        [SerializeField] Text listText;
        [SerializeField] Button backButton;

        void Start()
        {
            var collection = ServiceLocator.Instance.Get<CollectionService>();
            var owned = collection.GetOwned();
            listText.text = "角色: " + owned.Count;
            backButton.onClick.AddListener(() => UnityEngine.SceneManagement.SceneManager.LoadScene("Main"));
        }
    }
}
