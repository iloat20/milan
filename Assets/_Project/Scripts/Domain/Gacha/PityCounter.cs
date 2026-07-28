using System;
using Milan.Data;

namespace Milan.Domain.Gacha
{
    public class PityCounter
    {
        public int Counter = 0;
        public int Threshold;

        public PityCounter(int threshold)
        {
            Threshold = threshold;
        }

        public Rarity RollWithPity(Random rng, int[] rarityWeights, int minRarityForPity)
        {
            Counter++;
            if (Counter >= Threshold)
            {
                Counter = 0;
                return (Rarity)minRarityForPity;
            }
            return new GachaEngine(rng).RollRarity(rarityWeights);
        }

        public void Reset()
        {
            Counter = 0;
        }
    }
}
