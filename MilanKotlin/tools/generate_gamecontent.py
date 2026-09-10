#!/usr/bin/env python3
"""
从 data.json 自动生成 GameContent.kt 兜底内容。

用法：
    python tools/generate_gamecontent.py

data.json 是唯一 SoT（Single Source of Truth）。
本脚本读取 data.json 并生成等价的 GameContent.kt，
CI 会校验生成结果与仓库中的 GameContent.kt 是否一致。

生成的 GameContent.kt 包含：
  - buildCharacters()：从 Characters 数组构建角色列表
  - buildTalentTrees()：从 TalentTrees 数组构建天赋树
  - buildPools()：从 Pools 数组构建卡池
  - enrich()：从角色字段补齐派生字段（故事/语音/武器等）
"""

import json
import os
import sys
from pathlib import Path


def escape_kotlin_string(s: str) -> str:
    """转义 Kotlin 字符串中的特殊字符。"""
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def indent(text: str, level: int) -> str:
    """为文本的每一行添加缩进。"""
    prefix = "    " * level
    return "\n".join(prefix + line if line.strip() else "" for line in text.split("\n"))


def generate_build_characters(characters: list) -> str:
    """生成 buildCharacters() 方法体。"""
    lines = []
    for ch in characters:
        cid = ch["CharacterId"]
        name = ch["DisplayName"]
        title = ch["Title"]
        world = ch["World"]
        element = ch["Element"]
        rarity = ch["BaseRarity"]
        stats = ch["BaseStats"]
        stars = ch["MaxStars"]
        breakthrough = "true" if ch.get("CanBreakthrough", False) else "false"
        lore = escape_kotlin_string(ch.get("Lore", ""))
        tree_id = ch.get("TalentTreeId", "")
        skills = ch.get("Skills", [])

        skills_str = ", ".join(
            f'sk("{s["SkillId"]}", "{escape_kotlin_string(s["DisplayName"])}", '
            f'"{escape_kotlin_string(s["Description"])}", '
            f'"{s["Element"]}", "{s["Type"]}", {s["Power"]})'
            for s in skills
        )

        lines.append(
            f'        add(chars, "{cid}", "{escape_kotlin_string(name)}", '
            f'"{escape_kotlin_string(title)}", "{world}", "{element}", {rarity},'
        )
        lines.append(
            f'            listOf({", ".join(str(s) for s in stats)}), {stars}, {breakthrough},'
        )
        lines.append(f'            "{lore}",')
        lines.append(f'            "{tree_id}", listOf(')
        lines.append(f'                {skills_str},')
        lines.append(f'            ))')

    return "\n".join(lines)


def generate_build_talent_trees(talent_trees: list) -> str:
    """生成 buildTalentTrees() 方法体（直接从 data.json TalentTrees 构建）。"""
    lines = []
    for tree in talent_trees:
        tree_id = tree["TreeId"]
        nodes = tree.get("Nodes", [])
        node_lines = []
        for node in nodes:
            node_id = node["NodeId"]
            name = escape_kotlin_string(node["DisplayName"])
            desc = escape_kotlin_string(node["Description"])
            branch = node.get("BranchId", "")
            cost = node.get("Cost", 1)
            prereqs = node.get("PrerequisiteNodeIds", [])
            prereq_str = (
                f'listOf({", ".join(f"{chr(34)}{p}{chr(34)}" for p in prereqs)})'
                if prereqs
                else "emptyList()"
            )
            effects = node.get("Effects", [])
            effect_lines = []
            for eff in effects:
                eff_type = eff.get("Type", "")
                eff_val = eff.get("Value", 0)
                eff_chance = eff.get("Chance", 0)
                eff_dur = eff.get("Duration", 0)
                effect_lines.append(
                    f'TalentEffect(type = TalentEffectType.{eff_type}, value = {eff_val}f, '
                    f'chance = {eff_chance}f, duration = {eff_dur})'
                )
            effects_str = ", ".join(effect_lines)

            node_lines.append(
                f'                TalentNodeData(\n'
                f'                    nodeId = "{node_id}",\n'
                f'                    displayName = "{name}",\n'
                f'                    description = "{desc}",\n'
                f'                    branchId = "{branch}",\n'
                f'                    cost = {cost},\n'
                f'                    prerequisiteNodeIds = {prereq_str},\n'
                f'                    effects = listOf({effects_str}),\n'
                f'                )'
            )

        lines.append(f'        "{tree_id}" to TalentTreeData(')
        lines.append(f'            treeId = "{tree_id}",')
        lines.append(f'            nodes = listOf(')
        lines.append(",\n".join(node_lines))
        lines.append(f'            ),')
        lines.append(f'        ),')

    return "\n".join(lines)


def generate_enrich_map(name: str, data: dict, field: str, indent_level: int = 2) -> str:
    """生成 enrich() 中的 map 字面量。"""
    prefix = "    " * indent_level
    entries = []
    for ch in data:
        cid = ch["CharacterId"]
        val = ch.get(field, "")
        if isinstance(val, list):
            val = escape_kotlin_string(", ".join(val))
        else:
            val = escape_kotlin_string(str(val))
        if val:
            entries.append(f'{prefix}"{cid}" to "{val}"')
    return f"val {name}: Map<String, String> = mapOf(\n" + ",\n".join(entries) + f",\n{prefix[:-4]})"


def generate_enrich_map_list(name: str, data: dict, field: str, indent_level: int = 2) -> str:
    """生成 enrich() 中的 Map<String, List<String>> 字面量。"""
    prefix = "    " * indent_level
    entries = []
    for ch in data:
        cid = ch["CharacterId"]
        vals = ch.get(field, [])
        if vals:
            items = ", ".join(f'"{escape_kotlin_string(v)}"' for v in vals)
            entries.append(f'{prefix}"{cid}" to listOf({items})')
    return f"val {name}: Map<String, List<String>> = mapOf(\n" + ",\n".join(entries) + f",\n{prefix[:-4]})"


def generate_file(data: dict) -> str:
    """生成完整的 GameContent.kt 文件内容。"""
    characters = data.get("Characters", [])
    talent_trees = data.get("TalentTrees", [])
    pools = data.get("Pools", [])

    # --- buildCharacters ---
    chars_body = generate_build_characters(characters)

    # --- buildTalentTrees ---
    trees_body = generate_build_talent_trees(talent_trees)

    # --- enrich maps ---
    enrich_stories = generate_enrich_map("stories", characters, "Story")
    enrich_voices = generate_enrich_map_list("voices", characters, "Voices")
    enrich_weapon_vfx = generate_enrich_map("weaponVfx", characters, "WeaponVfx")
    enrich_ambient_vfx = generate_enrich_map("ambientVfx", characters, "AmbientVfx")
    enrich_weapon_name = generate_enrich_map("weaponNameMap", characters, "Weapon")
    enrich_weapon_desc = generate_enrich_map("weaponDescMap", characters, "WeaponDesc")

    # --- buildPools ---
    pool_entries = []
    for pool in pools:
        pid = pool["PoolId"]
        display = escape_kotlin_string(pool["DisplayName"])
        rarity_weights = pool.get("RarityWeights", [])
        hard_pity = pool.get("HardPity", 90)
        single_cost = pool.get("SingleCost", 160)
        ten_cost = pool.get("TenCost", 1600)
        featured = pool.get("FeaturedCharacterId", "")
        entries_data = pool.get("Entries", [])

        entries_lines = []
        for e in entries_data:
            eid = e["CharacterId"]
            rarity_idx = e.get("RarityIndex", 0)
            weight = e.get("Weight", 1)
            entries_lines.append(
                f'                GachaPoolEntry(characterId = "{eid}", rarityIndex = {rarity_idx}, weight = {weight}),'
            )

        pool_entries.append(f"""        GachaPoolDataEntry(
            poolId = "{pid}",
            displayName = "{display}",
            rarityWeights = listOf({", ".join(str(w) for w in rarity_weights)}),
            hardPity = {hard_pity},
            singleCost = {single_cost},
            tenCost = {ten_cost},
            featuredCharacterId = "{featured}",
            entries = listOf(
{chr(10).join(entries_lines)}
            ),
        )""")

    pools_body = ",\n".join(pool_entries)

    # --- 组装文件 ---
    outlander_ids = [
        ch["CharacterId"]
        for ch in characters
        if ch.get("Faction", ch.get("World", "")) == "域外"
    ]
    outlander_set = ", ".join(f'"{oid}"' for oid in outlander_ids)

    file_content = f'''package com.milan.game.services

import com.milan.game.domain.progression.TalentEffect
import com.milan.game.domain.progression.TalentEffectType

/**
 * GameService 的内置兜底内容。
 *
 * 本文件由 tools/generate_gamecontent.py 从 data.json 自动生成。
 * 请勿手动编辑！修改内容请更新 data.json 后重新运行生成器。
 *
 * 定位：data.json 缺失/损坏时的兜底副本，不是内容主来源。
 * 两条加载路径都必须经过 [enrich] 补齐派生字段，保证字段口径一致。
 */
internal object GameContent {{

    // ------------------------------------------------------------------ characters

    private fun add(
        list: MutableList<CharacterDataEntry>,
        id: String, name: String, title: String, world: String, element: String,
        rarity: Int, stats: List<Int>, stars: Int, breakthrough: Boolean,
        lore: String, treeId: String, skills: List<SkillData>,
    ) {{
        list += CharacterDataEntry(
            characterId = id, displayName = name, title = title, world = world, element = element,
            baseRarity = rarity, baseStats = stats, maxStage = 4, maxStars = stars,
            canBreakthrough = breakthrough, lore = lore, talentTreeId = treeId, skills = skills,
        )
    }}

    private fun sk(id: String, name: String, desc: String, element: String, type: String, power: Int): SkillData =
        SkillData(skillId = id, displayName = name, description = desc, element = element, type = type, power = power)

    /** 构建兜底角色表（从 data.json 自动生成）。 */
    fun buildCharacters(): List<CharacterDataEntry> {{
        val chars = mutableListOf<CharacterDataEntry>()
{chars_body}

        // 域外来客阵营：跨三界但同属「域外」
        val outlanderIds = setOf({outlander_set})
        chars.forEach {{ if (it.characterId in outlanderIds) it.faction = "域外" }}
        return chars
    }}

    // ------------------------------------------------------------------ talent trees

    /** 为每名角色构建天赋树（从 data.json 自动生成）。 */
    fun buildTalentTrees(characters: List<CharacterDataEntry>): List<TalentTreeData> {{
        // 从 data.json 的 TalentTrees 构建
        val treesById = mapOf(
{trees_body}
        )
        return characters.mapNotNull {{ treesById[it.talentTreeId] }}
    }}

    // ------------------------------------------------------------------ pools

    /** 构建兜底卡池（从 data.json 自动生成）。 */
    fun buildPools(characters: List<CharacterDataEntry>): List<GachaPoolDataEntry> {{
        fun entryFor(c: CharacterDataEntry) = GachaPoolEntry(
            characterId = c.characterId,
            rarityIndex = c.baseRarity,
            weight = when (c.baseRarity) {{
                4 -> 1
                3 -> 8
                2 -> 40
                else -> 100
            }},
        )
        val all = characters.map(::entryFor)
        return listOf(
{pools_body}
        )
    }}

    // ------------------------------------------------------------------ enrich

    /**
     * 补齐派生字段（阵营/背景故事/语音/武器名与描述），对齐 C# EnrichCharacters。
     * 两条加载路径（data.json / 兜底）都必须调用，保证字段口径一致。
     * 只补**为空**的字段：data.json 里已有值的角色不被字典覆盖。
     */
    fun enrich(characters: List<CharacterDataEntry>) {{
{enrich_stories}

{enrich_voices}

{enrich_weapon_vfx}

{enrich_ambient_vfx}

{enrich_weapon_name}

{enrich_weapon_desc}

        for (c in characters) {{
            if (c.faction.isEmpty()) c.faction = c.world
            if (c.story.isEmpty()) c.story = stories[c.characterId] ?: ""
            if (c.voices.isEmpty()) c.voices = voices[c.characterId] ?: listOf("……")
            if (c.weaponVfx.isEmpty()) c.weaponVfx = weaponVfx[c.characterId] ?: ""
            if (c.ambientVfx.isEmpty()) c.ambientVfx = ambientVfx[c.characterId] ?: ""
            if (c.weapon.isEmpty()) c.weapon = weaponNameMap[c.characterId] ?: ""
            if (c.weaponDesc.isEmpty()) c.weaponDesc = weaponDescMap[c.characterId] ?: ""
        }}
    }}
}}
'''
    return file_content


def main():
    # 定位 data.json
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent
    data_json_path = project_root / "app" / "src" / "main" / "assets" / "data.json"
    # GameContent 属于 :core 模块（领域内容兜底），不是 :app
    output_path = project_root / "core" / "src" / "main" / "java" / "com" / "milan" / "game" / "services" / "GameContent.kt"

    if not data_json_path.exists():
        print(f"错误：找不到 data.json：{data_json_path}", file=sys.stderr)
        sys.exit(1)

    with open(data_json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    generated = generate_file(data)

    # 写入输出文件
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(generated)

    print(f"已生成 {output_path}")
    print(f"  角色：{len(data.get('Characters', []))}")
    print(f"  天赋树：{len(data.get('TalentTrees', []))}")
    print(f"  卡池：{len(data.get('Pools', []))}")


if __name__ == "__main__":
    main()
