namespace Milan.Infrastructure.EventBus
{
    public struct GachaResultEvent { public string ItemId; public bool IsNew; public int Rarity; }
    public struct CharacterLevelUpEvent { public string CharacterId; public int NewLevel; }
    public struct CharacterStageUpEvent { public string CharacterId; public int NewStage; }
    public struct TalentAllocatedEvent { public string CharacterId; public string NodeId; }
    public struct CharacterBreakthroughEvent { public string CharacterId; }
    public struct BattleCompletedEvent { public string StageId; public bool Victory; }
}
