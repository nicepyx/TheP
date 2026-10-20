package com.thep.core.data

import com.thep.core.database.*

internal object Seeds {
    val days =
        listOf(
            DayEntity("mon", "胸 + 三头", 1),
            DayEntity("tue", "背 + 二头 + 腹部", 2),
            DayEntity("thu", "肩 + 上胸 + 腹部", 4),
            DayEntity("sat", "腿部", 6),
        )
    private val definitions =
        listOf(
            listOf("mon", "bench", "平板卧推", "胸", "3", "8", "12", "150"),
            listOf("mon", "inclineDb", "上斜哑铃卧推", "胸", "3", "8", "12", "150"),
            listOf("mon", "pecDeck", "蝴蝶机夹胸", "胸", "2", "12", "15", "75"),
            listOf("mon", "pushdown", "绳索三头下压", "肱三头肌", "2", "10", "15", "75"),
            listOf("tue", "pulldown", "高位下拉", "背", "3", "8", "12", "135"),
            listOf("tue", "cableRow", "坐姿划船", "背", "3", "8", "12", "135"),
            listOf("tue", "dbCurl", "哑铃弯举", "肱二头肌", "2", "10", "15", "75"),
            listOf("tue", "crunch", "卷腹", "腹部", "2", "12", "20", "60"),
            listOf("thu", "shoulderPress", "坐姿肩推", "肩", "3", "8", "12", "135"),
            listOf("thu", "lateralRaise", "哑铃侧平举", "肩", "3", "12", "20", "75"),
            listOf("thu", "reverseFly", "反向蝴蝶机", "肩", "2", "12", "20", "75"),
            listOf("thu", "inclineMachine", "上斜器械卧推", "胸", "2", "10", "12", "120"),
            listOf("thu", "cableCrunch", "绳索卷腹", "腹部", "2", "12", "15", "75"),
            listOf("sat", "legPress", "腿举", "腿", "3", "8", "12", "150"),
            listOf("sat", "legExtension", "腿屈伸", "腿", "2", "10", "15", "75"),
            listOf("sat", "legCurl", "坐姿腿弯举", "腿", "3", "10", "15", "90"),
            listOf("sat", "calfRaise", "站姿提踵", "小腿", "2", "12", "20", "75"),
        )
    val exercises = definitions.map { ExerciseEntity(it[1], it[2], it[3], "器械 / 自重") }
    val moves =
        definitions.mapIndexed { i, it ->
            MoveEntity(
                "${it[0]}-${it[1]}",
                it[0],
                it[1],
                i,
                it[4].toInt(),
                it[5].toInt(),
                it[6].toInt(),
                it[7].toInt(),
            )
        }
}
