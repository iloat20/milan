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
            int total = 0;
            foreach (var w in weights) total += w;
            int roll = _rng.Next(total);
            int cumulative = 0;
            for (int i = 0; i < ids.Length; i++)
            {
                cumulative += weights[i];
                if (roll < cumulative) return ids[i];
            }
            return ids[ids.Length - 1];
        }
    }
}
