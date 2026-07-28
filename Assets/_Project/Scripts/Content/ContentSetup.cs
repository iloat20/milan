#if UNITY_EDITOR
using UnityEditor;
using UnityEngine;
using Milan.Data;
using Milan.Data.ScriptableObjects;
using System.IO;

namespace Milan.Content
{
    public class ContentSetup
    {
        [MenuItem("Milan/Create MVP Content")]
        public static void Create()
        {
            EnsureDir("Worlds");
            EnsureDir("Characters");
            EnsureDir("CharacterLore");
            EnsureDir("TalentTrees");
            EnsureDir("TalentNodes");
            EnsureDir("Skins");
            EnsureDir("Items");
            EnsureDir("GachaPools");

            CreateSO<WorldData>("Worlds/World_Shinwa", w =>
            {
                w.WorldType = WorldType.Shinwa;
                w.DisplayName = "和忍 Shinwa";
                w.Description = "东方忍者奇幻次元。";
                w.UseCelShading = true;
            });
            CreateSO<WorldData>("Worlds/World_Aether", w =>
            {
                w.WorldType = WorldType.Aether;
                w.DisplayName = "星灵 Aether";
                w.Description = "宇宙能量科幻次元（占位）。";
                w.UseCelShading = false;
            });

            CreateSO<CharacterData>("Characters/Char_Kasai", c =>
            {
                c.CharacterId = "char_kasai";
                c.DisplayName = "烬 Kasai";
                c.World = WorldType.Shinwa;
                c.BaseRarity = Rarity.SSR;
                c.TalentTreeId = "tree_kasai";
                c.DefaultSkinId = "skin_kasai_1";
                c.BaseStats = new[] { 120, 80, 1000, 15 };
                c.MaxStage = 4;
                c.MaxStars = 6;
                c.CanBreakthrough = true;
            });
            CreateSO<CharacterData>("Characters/Char_Hikari", c =>
            {
                c.CharacterId = "char_hikari";
                c.DisplayName = "光 Hikari";
                c.World = WorldType.Aether;
                c.BaseRarity = Rarity.SR;
                c.TalentTreeId = "tree_hikari";
                c.DefaultSkinId = "skin_hikari_1";
                c.BaseStats = new[] { 90, 70, 900, 12 };
                c.MaxStage = 4;
                c.MaxStars = 5;
                c.CanBreakthrough = false;
            });

            CreateSO<GachaPoolData>("GachaPools/Pool_Main", p =>
            {
                p.PoolId = "pool_main";
                p.DisplayName = "次元裂缝 · 常驻";
                p.RarityWeights = new[] { 820, 150, 29, 1 };
                p.HardPity = 90;
                p.CostItemId = "item_soft_currency";
                p.SingleCost = 160;
                p.TenCost = 1600;
            });

            CreateSO<ItemData>("Items/Item_SoftCurrency", i =>
            {
                i.ItemId = "item_soft_currency";
                i.DisplayName = "星尘";
                i.Type = ItemType.SoftCurrency;
                i.MaxStack = 999999;
            });

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("[Milan] MVP content created. Add more characters/talents/skins as needed.");
        }

        static void EnsureDir(string name)
        {
            var path = "Assets/_Project/Scripts/Content/" + name;
            if (!AssetDatabase.IsValidFolder(path))
            {
                var parent = Path.GetDirectoryName(path).Replace("\\", "/");
                AssetDatabase.CreateFolder(parent, name);
            }
        }

        static void CreateSO<T>(string path, System.Action<T> init) where T : ScriptableObject
        {
            var asset = ScriptableObject.CreateInstance<T>();
            init(asset);
            var fullPath = "Assets/_Project/Scripts/Content/" + path + ".asset";
            AssetDatabase.CreateAsset(asset, fullPath);
        }
    }
}
#endif
