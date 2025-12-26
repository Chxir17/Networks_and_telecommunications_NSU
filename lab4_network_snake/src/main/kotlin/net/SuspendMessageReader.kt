package onlinesnake.net

interface SuspendMessageReader {
    suspend fun read(): SerializedMessage
}