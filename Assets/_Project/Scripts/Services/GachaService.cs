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

        public string Pull(GachaPoolData pool, bool tenPull, string[] characterIds, int[] weights)
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
                var rarity = pity.RollWithPity(_rng, pool.RarityWeights, 3);
                var id = _engine.PickWeighted(characterIds, weights);
                if (i == count - 1) resultId = id;
                GrantItem(id);
            }
            data.SetGachaCounter(pool.PoolId, pity.Counter);
            _save.Save();
            EventBus.Publish(new GachaResultEvent { ItemId = resultId, IsNew = true, Rarity = 4 });
            return resultId;
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
