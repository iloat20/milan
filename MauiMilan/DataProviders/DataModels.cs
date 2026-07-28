namespace Milan.Maui.DataProviders;

// MAUI 用的角色数据（替代 Unity ScriptableObject）
public class CharacterDataEntry
{
    public string CharacterId = "";
    public string DisplayName = "";
    public string World = "Shinwa";
    public int BaseRarity = 1;        // 1=R, 2=SR, 3=SSR, 4=UR
    public int[] BaseStats = { 100, 80, 1000, 12 }; // ATK, DEF, HP, SPD
    public int MaxStage = 4;
    public int MaxStars = 5;
    public bool CanBreakthrough;
}

// 卡池条目
public class GachaPoolEntry
{
    public string CharacterId = "";
    public int RarityIndex = 1;
    public int Weight = 100;
}

// 卡池数据
public class GachaPoolDataEntry
{
    public string PoolId = "";
    public string DisplayName = "";
    public int[] RarityWeights = { 820, 150, 29, 1 };
    public int HardPity = 90;
    public int SingleCost = 160;
    public int TenCost = 1600;
    public List<GachaPoolEntry> Entries = new();
}
