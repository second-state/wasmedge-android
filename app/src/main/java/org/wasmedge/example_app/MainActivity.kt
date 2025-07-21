package org.wasmedge.example_app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.wasmedge.native_lib.NativeLib
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    lateinit var lib: NativeLib
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvResponse: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoading: TextView
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etInput = findViewById(R.id.et_input)
        btnSend = findViewById(R.id.btn_send)
        tvStatus = findViewById(R.id.tv_status)
        tvResponse = findViewById(R.id.tv_response)
        progressBar = findViewById(R.id.progress_bar)
        tvLoading = findViewById(R.id.tv_loading)

        lib = NativeLib(this)

        Thread {
            lib.llamaApiServer()
            runOnUiThread {
                tvStatus.text = "Server Status: Running on port 8080"
            }
        }.start()

        btnSend.setOnClickListener {
            val userInput = etInput.text.toString().trim()
            if (userInput.isNotEmpty()) {
                sendChatRequest(userInput)
            }
        }
    }

    private fun sendChatRequest(userMessage: String) {
        showLoading(true)
        btnSend.isEnabled = false

        val jsonObject = JSONObject()
        val messagesArray = JSONArray()
        
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", "You are a helpful assistant.")
        
        val userMessageObj = JSONObject()
        userMessageObj.put("role", "user")
        userMessageObj.put("content", userMessage)
        
        messagesArray.put(systemMessage)
        messagesArray.put(userMessageObj)
        jsonObject.put("messages", messagesArray)

        val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("http://localhost:8080/v1/chat/completions")
            .post(requestBody)
            .addHeader("accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    btnSend.isEnabled = true
                    tvResponse.text = "Error: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        null
                    }
                    
                    runOnUiThread {
                        showLoading(false)
                        btnSend.isEnabled = true
                        
                        if (response.isSuccessful && responseBody != null) {
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                val choices = jsonResponse.getJSONArray("choices")
                                val message = choices.getJSONObject(0).getJSONObject("message")
                                val content = message.getString("content")
                                
                                tvResponse.text = "Response: $content"
                            } catch (e: Exception) {
                                tvResponse.text = "Error parsing response: ${e.message}"
                            }
                        } else {
                            tvResponse.text = "HTTP Error: ${response.code} - ${response.message}"
                        }
                    }
                }
            }
        })
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            progressBar.visibility = android.view.View.VISIBLE
            tvLoading.visibility = android.view.View.VISIBLE
        } else {
            progressBar.visibility = android.view.View.GONE
            tvLoading.visibility = android.view.View.GONE
        }
    }
}
