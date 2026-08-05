namespace Milan.Infrastructure.EventBus;

/// <summary>
/// 经济变动（星尘 / 钻石）标记事件。
/// 作为"轻标记"使用：handler 收到后直接重读 GameState.Currency /
/// GameState.Service.SaveData.HardCurrency 的当前值，而不是依赖负载里的旧数值，
/// 避免队列中连续多次扣费时负载过期导致显示陈旧。
/// 星尘与钻石共用一个事件，因为订阅方本来就会同时刷新两个文本控件（YAGNI：不拆成两个事件）。
/// </summary>
public readonly struct CurrencyChanged { }

/// <summary>
/// 养成变动（升级 / 突破 / 天赋加点）标记事件。
/// 作为"轻标记"使用（无负载）：handler 收到后直接重读当前角色存档的实时值，
/// 而不是依赖负载里的旧数值，避免连续多次养成操作时负载过期导致显示陈旧。
/// 养成页与详情页都订阅它来刷新各自面板（养成页自身操作后会直接 Refresh，
/// 订阅仅用于跨页一致性，例如从养成页返回详情页时属性随动）。
/// </summary>
public readonly struct ProgressionChanged { }
