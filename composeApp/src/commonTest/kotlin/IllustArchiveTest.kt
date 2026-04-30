import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.kagg886.pmf.backend.archive.archiveKeyParts

class IllustArchiveTest {
    @Test
    fun archiveKeyPartsResolvesValidArchiveUri() {
        assertEquals(
            123 to "page-0.jpg",
            archiveKeyParts("pixiv-archive://123/page-0.jpg"),
        )
    }

    @Test
    fun archiveKeyPartsRejectsMalformedArchiveUri() {
        assertNull(archiveKeyParts("https://example.com/page-0.jpg"))
        assertNull(archiveKeyParts("pixiv-archive://123"))
        assertNull(archiveKeyParts("pixiv-archive://not-a-number/page-0.jpg"))
    }
}
