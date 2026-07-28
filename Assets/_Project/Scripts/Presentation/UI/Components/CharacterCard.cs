using UnityEngine;
using UnityEngine.UI;

namespace Milan.Presentation.UI.Components
{
    public class CharacterCard : MonoBehaviour
    {
        [SerializeField] Text nameText;
        public void SetName(string name) { if (nameText != null) nameText.text = name; }
    }
}
