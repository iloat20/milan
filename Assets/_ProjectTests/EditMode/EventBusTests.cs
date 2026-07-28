using NUnit.Framework;
using Milan.Infrastructure.EventBus;

[TestFixture]
public class EventBusTests
{
    [TearDown] public void TearDown() => EventBus.Clear();

    [Test]
    public void Subscribe_And_Publish_Delivers_Event()
    {
        int received = 0;
        EventBus.Subscribe<int>(e => received = e);
        EventBus.Publish(42);
        EventBus.Dispatch();
        Assert.AreEqual(42, received);
    }

    [Test]
    public void Dispatch_Clears_Queue()
    {
        int calls = 0;
        EventBus.Subscribe<int>(e => calls++);
        EventBus.Publish(1);
        EventBus.Dispatch();
        EventBus.Dispatch();
        Assert.AreEqual(1, calls);
    }

    [Test]
    public void Unsubscribe_Stops_Delivery()
    {
        int calls = 0;
        System.Action<int> handler = e => calls++;
        EventBus.Subscribe(handler);
        EventBus.Unsubscribe(handler);
        EventBus.Publish(1);
        EventBus.Dispatch();
        Assert.AreEqual(0, calls);
    }
}
