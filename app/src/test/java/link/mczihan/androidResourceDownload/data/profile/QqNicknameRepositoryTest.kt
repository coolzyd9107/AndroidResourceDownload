package link.mczihan.androidResourceDownload.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QqNicknameRepositoryTest {
    @Test
    fun parsesExpectedQqNickname() {
        val payload =
            "portraitCallBack({\"123456\":[\"avatar\",-1,0,0,0,0,\"测试昵称\",0]});"

        assertEquals("测试昵称", parseQqNickname(payload, "123456"))
    }

    @Test
    fun rejectsWrongIdentityAndInvalidWrapper() {
        val payload =
            "portraitCallBack({\"654321\":[\"avatar\",-1,0,0,0,0,\"Other\",0]});"

        assertNull(parseQqNickname(payload, "123456"))
        assertNull(parseQqNickname("_Callback({\"error\":{}});", "123456"))
    }
}
