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
            // 保底阈值无效（配置为 0 或负数）→ 不触发保底，按自然概率抽取，
            // 否则每抽必触发 -> 卡池恒为保底稀有度、且永远抽不到更低配的 UR/SSR。
            if (Threshold <= 0)
                return new GachaEngine(rng).RollRarity(rarityWeights);
            Counter++;
            if (Counter >= Threshold)
            {
                Counter = 0;
                return (Rarity)minRarityForPity;
            }
            var rolled = new GachaEngine(rng).RollRarity(rarityWeights);
            // 自然抽出保底档及以上稀有度时同样重置计数器（标准保底语义），
            // 否则玩家会在自然出货后紧接着又吃保底，概率被双倍放大。
            if ((int)rolled >= minRarityForPity)
                Counter = 0;
            return rolled;
        }

        public void Reset()
        {
            Counter = 0;
        }
    }
}
