using System.Collections.Generic;
using UnityEngine;
using Milan.Data;
using Milan.Data.ScriptableObjects;
using Milan.Domain.Gacha;
using Milan.Infrastructure.EventBus;
using Milan.Infrastructure.Save;

namespace Milan.Services
{
    public class GachaService
    {
        readonly SaveManager _save;
        readonly GachaEngine _engine;
        readonly System.Random _rng = new System.Random();

        public GachaService(SaveManager save)
        {
            _save = save;
            _engine = new GachaEngine(_rng);
        }

        public GachaPoolData[] GetAllPools()
        {
            return Resources.LoadAll<GachaPoolData>("Content/GachaPools");
        }

        public string Pull(GachaPoolData pool, bool tenPull)
        {
            var data = _save.Current;
            int count = tenPull ? 10 : 1;
            int cost = tenPull ? pool.TenCost : pool.SingleCost;
            if (data.SoftCurrency < cost) return null;
            data.SoftCurrency -= cost;

            var pity = GetPity(pool.PoolId, pool.HardPity);
            string resultId = null;
            for (int i = 0; i < count; i++)
            {
                // 先掷稀有度（含保底）
                Rarity rarity = pity.RollWithPity(_rng, pool.RarityWeights, 3);
                // 再在该稀有度内按权重抽角色
                var entries = pool.GetEntriesForRarity(rarity);
                string id = PickFromEntries(entries);
                if (string.IsNullOrEmpty(id) && entries.Count == 0)
                {
                    // 该稀有度无角色时，回退到任意稀有度的角色
                    id = PickFromAllEntries(pool);
                }
                if (i == count - 1) resultId = id;
                bool isNew = !string.IsNullOrEmpty(id) && !OwnsCharacter(id);
                if (!string.IsNullOrEmpty(id))
                {
                    GrantItem(id);
                    EventBus.Publish(new GachaResultEvent { ItemId = id, IsNew = isNew, Rarity = (int)rarity });
                }
            }
            data.SetGachaCounter(pool.PoolId, pity.Counter);
            _save.Save();
            return resultId;
        }

        string PickFromEntries(List<GachaPoolEntry> entries)
        {
            if (entries == null || entries.Count == 0) return null;
            var ids = new List<string>();
            var weights = new List<int>();
            foreach (var e in entries) { ids.Add(e.CharacterId); weights.Add(e.Weight); }
            if (ids.Count == 0) return null;
            return _engine.PickWeighted(ids.ToArray(), weights.ToArray());
        }

        string PickFromAllEntries(GachaPoolData pool)
        {
            if (pool.Entries == null || pool.Entries.Length == 0) return null;
            var ids = new List<string>();
            var weights = new List<int>();
            foreach (var e in pool.Entries) { ids.Add(e.CharacterId); weights.Add(e.Weight); }
            return _engine.PickWeighted(ids.ToArray(), weights.ToArray());
        }

        bool OwnsCharacter(string id)
        {
            return _save.Current.OwnedCharacters.Exists(c => c.CharacterId == id);
        }

        void GrantItem(string id)
        {
            var data = _save.Current;
            if (data.OwnedCharacters.Find(c => c.CharacterId == id) != null)
            {
                var frag = data.Items.Find(i => i.ItemId == id + "_frag");
                if (frag != null) frag.Count += 10;
            }
            else
            {
                data.OwnedCharacters.Add(new CharacterSaveState { CharacterId = id });
            }
        }

        PityCounter GetPity(string poolId, int threshold)
        {
            return new PityCounter(threshold) { Counter = _save.Current.GetGachaCounter(poolId) };
        }
    }
}
