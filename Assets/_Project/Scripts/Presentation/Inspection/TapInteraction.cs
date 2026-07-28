using UnityEngine;

namespace Milan.Presentation.Inspection
{
    public class TapInteraction : MonoBehaviour
    {
        void Update()
        {
            if (Input.touchCount == 1 && Input.GetTouch(0).phase == TouchPhase.Began)
            {
                var ray = Camera.main.ScreenPointToRay(Input.GetTouch(0).position);
                if (Physics.Raycast(ray, out var hit))
                {
                    Debug.Log("[Milan] Tapped part: " + hit.collider.name);
                    var anim = hit.collider.GetComponent<Animator>();
                    if (anim) anim.SetTrigger("TapReact");
                }
            }
        }
    }
}
