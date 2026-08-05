using System;
using Milan.Data;

namespace Milan.Domain.Gacha
{
    public class GachaEngine
    {
        readonly Random _rng;
        public GachaEngine(Random rng = null)
        {
            _rng = rng ?? new Random();
        }

        public Rarity RollRarity(int[] rarityWeights)
        {
            int total = 0;
            foreach (var w in rarityWeights) total += w;
            if (total <= 0) return Rarity.R; // 权重和为 0 时 Next(0) 会抛异常
            int roll = _rng.Next(total);
            int cumulative = 0;
            for (int i = 0; i < rarityWeights.Length; i++)
            {
                cumulative += rarityWeights[i];
                if (roll < cumulative) return (Rarity)(i + 1);
            }
            return Rarity.UR;
        }

        public string PickWeighted(string[] ids, int[] weights)
        {
            if (ids == null || ids.Length == 0 || weights == null) return null;
            int total = 0;
            int n = Math.Min(ids.Length, weights.Length);
            for (int i = 0; i < n; i++) total += weights[i];
            if (total <= 0) return ids[0];
            int roll = _rng.Next(total);
            int cumulative = 0;
            for (int i = 0; i < n; i++)
            {
                cumulative += weights[i];
                if (roll < cumulative) return ids[i];
            }
            return ids[n - 1];
        }
    }
}
