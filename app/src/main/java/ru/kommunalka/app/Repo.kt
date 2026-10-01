package ru.kommunalka.app

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Хранит все данные в одном JSON-файле во внутренней памяти приложения. */
class Repo private constructor(private val dir: File) {

    private val file = AtomicFile(File(dir, "state.json"))

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** false — последняя запись на диск не удалась (мало места и т. п.). */
    private val _saveOk = MutableStateFlow(true)
    val saveOk: StateFlow<Boolean> = _saveOk.asStateFlow()

    @Synchronized
    fun update(f: (AppState) -> AppState) {
        val s = f(_state.value)
        _state.value = s
        save(s)
    }

    fun updateApt(aptId: String, f: (Apartment) -> Apartment) =
        update { s -> s.copy(apts = s.apts.map { if (it.id == aptId) f(it) else it }) }

    fun updateMeter(aptId: String, meterId: String, f: (Meter) -> Meter) =
        updateApt(aptId) { a -> a.copy(meters = a.meters.map { if (it.id == meterId) f(it) else it }) }

    private fun load(): AppState {
        if (!file.baseFile.exists()) return AppState()
        return try {
            stateFromJson(JSONObject(String(file.readFully(), Charsets.UTF_8)))
        } catch (e: Exception) {
            // Файл повреждён. Не перезаписываем его молча: откладываем копию,
            // чтобы данные можно было восстановить вручную.
            Log.e(TAG, "Не удалось прочитать данные, файл сохранён как копия", e)
            try {
                file.baseFile.copyTo(File(dir, "state.broken-${System.currentTimeMillis()}.json"), overwrite = true)
            } catch (copyError: Exception) {
                Log.e(TAG, "Не удалось сохранить копию повреждённого файла", copyError)
            }
            AppState()
        }
    }

    private fun save(s: AppState) {
        val bytes = try {
            s.toJson().toString().toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось подготовить данные к записи", e)
            _saveOk.value = false
            return
        }
        // AtomicFile пишет во временный файл и подменяет основной только после успешной записи,
        // поэтому сбой или нехватка места посреди записи не испортят уже сохранённые данные.
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Не удалось открыть файл для записи", e)
            _saveOk.value = false
            return
        }
        try {
            out.write(bytes)
            file.finishWrite(out)
            _saveOk.value = true
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e(TAG, "Не удалось записать данные", e)
            _saveOk.value = false
        }
    }

    /* ---------- Резервная копия ---------- */

    /** Записывает все данные в выбранный пользователем файл. Вызывать не из главного потока. */
    fun exportTo(ctx: Context, uri: Uri) {
        val bytes = _state.value.toJson().toString(2).toByteArray(Charsets.UTF_8)
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("Файл недоступен для записи")
        out.use { it.write(bytes) }
    }

    /**
     * Читает резервную копию из выбранного файла и возвращает её содержимое (ещё не применённое).
     * Размер ограничен, содержимое проверяется так же строго, как при обычной загрузке.
     * Вызывать не из главного потока.
     */
    fun readBackup(ctx: Context, uri: Uri): AppState {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Файл недоступен для чтения")
        val bytes = input.use { stream ->
            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                buf.write(chunk, 0, n)
                if (buf.size() > MAX_BACKUP_BYTES) throw IOException("Файл слишком большой для резервной копии")
            }
            buf.toByteArray()
        }
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        if (!json.has("apts")) throw IOException("Это не резервная копия «Коммуналки»")
        return stateFromJson(json)
    }

    fun replaceAll(newState: AppState) = update { old -> newState.copy(notifyHour = old.notifyHour) }

    companion object {
        private const val TAG = "Kommunalka"
        private const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

        @Volatile
        private var inst: Repo? = null

        fun get(ctx: Context): Repo = inst ?: synchronized(this) {
            inst ?: Repo(ctx.applicationContext.filesDir).also { inst = it }
        }
    }
}
