using UnityEngine;

namespace Milan.Presentation.Inspection
{
    public class InspectionCamera : MonoBehaviour
    {
        [SerializeField] Transform target;
        [SerializeField] float distance = 3f;
        [SerializeField] float rotateSpeed = 0.2f;
        [SerializeField] float zoomSpeed = 0.5f;
        [SerializeField] float minDistance = 1f;
        [SerializeField] float maxDistance = 6f;

        float yaw;
        float pitch = 15f;

        void Update()
        {
            if (Input.touchCount == 1)
            {
                Touch t = Input.GetTouch(0);
                if (t.phase == TouchPhase.Moved)
                {
                    yaw += t.deltaPosition.x * rotateSpeed;
                    pitch -= t.deltaPosition.y * rotateSpeed;
                    pitch = Mathf.Clamp(pitch, -40f, 60f);
                }
            }
            if (Input.touchCount == 2)
            {
                Touch a = Input.GetTouch(0);
                Touch b = Input.GetTouch(1);
                float prev = (a.position - a.deltaPosition - (b.position - b.deltaPosition)).magnitude;
                float cur = (a.position - b.position).magnitude;
                distance -= (cur - prev) * zoomSpeed * 0.01f;
                distance = Mathf.Clamp(distance, minDistance, maxDistance);
            }

            if (target != null)
            {
                var rot = Quaternion.Euler(pitch, yaw, 0);
                transform.position = target.position - rot * Vector3.forward * distance;
                transform.LookAt(target);
            }
        }
    }
}
