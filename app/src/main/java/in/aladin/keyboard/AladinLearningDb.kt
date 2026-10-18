package `in`.aladin.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class AladinLearningDb(context: Context) :
    SQLiteOpenHelper(context, "aladin_learning.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE words(word TEXT PRIMARY KEY, count INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE pairs(previous_word TEXT NOT NULL, next_word TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(previous_word,next_word))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun increment(table: String, where: ContentValues, amount: Int = 1) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val key = if (table == "words") "word" else "previous_word"
            val nextKey = "next_word"
            val existing = if (table == "words") {
                db.rawQuery("SELECT count FROM words WHERE word=?", arrayOf(where.getAsString("word")))
            } else {
                db.rawQuery("SELECT count FROM pairs WHERE previous_word=? AND next_word=?", arrayOf(where.getAsString(key), where.getAsString(nextKey)))
            }
            var old = 0
            existing.use { if (it.moveToFirst()) old = it.getInt(0) }
            val values = ContentValues(where)
            values.put("count", old + amount)
            db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun incrementWord(word: String, amount: Int = 1) {
        if (word.isBlank()) return
        increment("words", ContentValues().apply { put("word", word) }, amount)
    }

    fun incrementPair(previous: String, next: String, amount: Int = 1) {
        if (previous.isBlank() || next.isBlank()) return
        increment("pairs", ContentValues().apply {
            put("previous_word", previous); put("next_word", next)
        }, amount)
    }

    fun setWordCount(word: String, count: Int) {
        if (word.isBlank()) return
        writableDatabase.insertWithOnConflict("words", null, ContentValues().apply {
            put("word", word); put("count", count.coerceAtLeast(1))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setPairCount(previous: String, next: String, count: Int) {
        if (previous.isBlank() || next.isBlank()) return
        writableDatabase.insertWithOnConflict("pairs", null, ContentValues().apply {
            put("previous_word", previous); put("next_word", next); put("count", count.coerceAtLeast(1))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun toSyncJson(): JSONObject {
        val result = JSONObject().put("format", "aladin-keyboard-sync-v1")
        val words = JSONArray()
        readableDatabase.rawQuery("SELECT word,count FROM words ORDER BY count DESC", null).use { c ->
            while (c.moveToNext()) words.put(JSONObject().put("word", c.getString(0)).put("count", c.getInt(1)))
        }
        val pairs = JSONArray()
        readableDatabase.rawQuery("SELECT previous_word,next_word,count FROM pairs ORDER BY count DESC", null).use { c ->
            while (c.moveToNext()) pairs.put(JSONObject().put("previous_word", c.getString(0)).put("next_word", c.getString(1)).put("count", c.getInt(2)))
        }
        return result.put("word_counts", words).put("word_pairs", pairs)
    }

    fun clearLearning() {
        writableDatabase.execSQL("DELETE FROM words")
        writableDatabase.execSQL("DELETE FROM pairs")
    }
}
