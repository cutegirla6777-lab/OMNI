// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class GeminiProvider(...) : AiProvider {") were not included in what was pasted.
// Body below is otherwise complete as received.

private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model"

override suspend fun generate(prompt: String): String {
    val start = System.currentTimeMillis()

    val json = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().apply {
                put("text", prompt)
            }))
        }))
        put("generationConfig", JSONObject().apply {
            put("temperature", 0.7)
            put("maxOutputTokens", 4096)
        })
    }

    val response = post("$baseUrl:generateContent?key=$apiKey", json)
    lastLatency = System.currentTimeMillis() - start

    return parseResponse(response)
}

override suspend fun generateStream(prompt: String): Flow<String> = flow {
    emit(generate(prompt))
}

override suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String {
    val base64Image = bitmapToBase64(bitmap)

    val json = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Image)
                    })
                })
                put(JSONObject().apply {
                    put("text", prompt)
                })
            })
        }))
    }

    val response = post("$baseUrl:generateContent?key=$apiKey", json)
    return parseResponse(response)
}

private suspend fun post(urlString: String, json: JSONObject): String {
    return withContext(Dispatchers.IO) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        connection.outputStream.use { it.write(json.toString().toByteArray()) }

        connection.inputStream.bufferedReader().readText()
    }
}

private fun parseResponse(response: String): String {
    val json = JSONObject(response)
    return json.getJSONArray("candidates")
        .getJSONObject(0)
        .getJSONObject("content")
        .getJSONArray("parts")
        .getJSONObject(0)
        .getString("text")
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

override fun recordSuccess() {
    successRate = (successRate * 9 + 1) / 10
}

override fun recordFailure(error: String?) {
    successRate = (successRate * 9) / 10
}
