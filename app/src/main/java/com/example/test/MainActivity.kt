package com.example.test

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.test.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.IOUtils
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        const val REQUEST_CODE_GOOGLE_SIGN_IN = 101
        const val REQUEST_CODE_SELECT_FILE = 111
        const val GOOGLE_DRIVE_FOLDER_ID = "drive_folder_id"
        private val REQUEST_AUTHORIZATION = 123
    }

    private val driveUtils by lazy {
        DriveUtils(this, getSharedPreferences("testDrive", MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        GoogleSignIn.getLastSignedInAccount(this)?.let {
            setUpView(it)
        }
        initListener()
    }

    private fun initListener() {
        binding.btnSign.setOnClickListener {
            signIn()
        }
        binding.btnChoose.setOnClickListener {
            selectFile()
        }
        binding.btnSignOut.setOnClickListener {
            signOut()
        }
        binding.btnSyn.setOnClickListener {
            uploadFile()
//            Log.d("ddddd", "Sync: name = ${fileUpload?.name} id = ${fileUpload?.id}")
        }
    }

    private fun uploadFile() {
        CoroutineScope(Dispatchers.Default).launch {
            GoogleSignIn.getLastSignedInAccount(applicationContext)?.let { account ->
                val credential = GoogleAccountCredential.usingOAuth2(
                    applicationContext, listOf(DriveScopes.DRIVE_FILE)
                )
                credential.selectedAccountName = account.account?.name
                val driveService = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                    .setApplicationName("TestDrive")
                    .build()
                val fileMeta = com.google.api.services.drive.model.File().apply {
                    name = "screenShot"
                    mimeType = "application/vnd.google-apps.file"
                }
                val filePath = File(fileSelected.toString())
                val fileContent = FileContent("image/png", filePath)
                try {
                    val file = driveService.files().create(fileMeta, fileContent)
                        .setFields("id")
                        .execute()
                    Log.d("ddddd", "Sync: name = ${file?.name} id = ${file?.id}")
                } catch (ex: UserRecoverableAuthIOException) {
                    Log.d("ddddd", "UserRecoverableAuthIOException: ${ex.printStackTrace()}")
                    startActivityForResult(ex.intent, REQUEST_AUTHORIZATION);
                } catch (e: Exception) {
                    Log.d("ddddd", "Sync exception: ${e.printStackTrace()}")
                }
            }
        }
//        driveUtils.uploadFileToGDrive2(File(fileSelected.toString()), "image/png", GOOGLE_DRIVE_FOLDER_ID)
    }

    private fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        client.signOut().addOnCompleteListener {
            binding.apply {
                imageAvatar.visibility = View.INVISIBLE
                tvEmail.text = ""
                tvName.text = ""
            }
        }
    }

    private fun signIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        val intent = client.signInIntent
        startActivityForResult(intent, REQUEST_CODE_GOOGLE_SIGN_IN)
    }

    private fun setUpView(x: GoogleSignInAccount) {
        Picasso.get().load(x.photoUrl).into(binding.imageAvatar)
        binding.imageAvatar.visibility = View.VISIBLE
        binding.tvEmail.text = x.email
        binding.tvName.text = x.displayName
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val x = task.getResult(ApiException::class.java)
                setUpView(x)
            } catch (e: Exception) {
                Log.d("dddd", "exception: ${e.printStackTrace()} ")
            }
        }
        if (requestCode == 123) {
            Log.d("dddd", "activityResult request authorization Drive")
        }
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_SELECT_FILE -> {
                    val selectedFile = data?.data
                    selectedFile?.let { makeCopy(it) }
                    Log.d("dddd", "select file ${selectedFile.toString()} ")
                    fileSelected = selectedFile
                    binding.imageSelected.apply {
                        setImageURI(null)
                        setImageURI(fileSelected)
                    }
                }
            }
        }
    }

    var fileSelected: Uri? = null
    private fun selectFile() {
        val intent = Intent()
            .setType("*/*")
            .setAction(Intent.ACTION_GET_CONTENT)
        startActivityForResult(Intent.createChooser(intent, "Select a file"), REQUEST_CODE_SELECT_FILE)
    }

    private fun makeCopy(fileUri: Uri) {
        val parcelFileDescriptor = contentResolver.openFileDescriptor(fileUri, "r", null)
        val inputStream = FileInputStream(parcelFileDescriptor?.fileDescriptor)
        val file = File(filesDir, getFileName(contentResolver, fileUri))
        val outputStream = FileOutputStream(file)
        IOUtils.copy(inputStream, outputStream)
    }

    private fun getFileName(resolver: ContentResolver, uri: Uri): String {
        var name = ""
        val returnCursor = resolver.query(uri, null, null, null, null)
        returnCursor?.let {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            name = it.getString(nameIndex)
            it.close()
        }
        return name
    }
}