package link.mczihan.androidResourceDownload.core.logging

import android.util.Log
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Test

class LogLineFormatterTest {
    @Test
    fun formatsMessageAndThrowableForFileOutput() {
        val output = LogLineFormatter(ZoneOffset.UTC).format(
            FileLogEntry(
                timestampEpochMillis = 0L,
                priority = Log.ERROR,
                tag = "WebDav",
                message = "Unable to parse response",
                throwable = IllegalStateException("parser failure"),
            ),
        )

        assertTrue(output.startsWith("1970-01-01 00:00:00.000 Z ERROR/WebDav: "))
        assertTrue(output.contains("Unable to parse response"))
        assertTrue(output.contains("IllegalStateException: parser failure"))
    }
}
