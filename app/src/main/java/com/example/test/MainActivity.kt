package com.example.test

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
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.util.IOUtils
import com.google.api.services.drive.model.FileList
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        const val REQUEST_CODE_GOOGLE_SIGN_IN = 101
        const val REQUEST_CODE_SELECT_FILE = 111
        const val GOOGLE_DRIVE_FOLDER_ID = "drive_folder_id"
        const val REQUEST_AUTHORIZATION = 1001
        const val TAG = "dddd"
    }

    private var idAccount = ""
    private val sharef by lazy {
        getSharedPreferences("testDrive", MODE_PRIVATE)
    }
    private val driveUtils by lazy {
        DriveUtils(this, sharef)
    }
    private lateinit var noteDatabase: NoteDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        GoogleSignIn.getLastSignedInAccount(this)?.let {
            setUpView(it)
        }
        noteDatabase = DatabaseBuilder.getInstance(this)
        CoroutineScope(Dispatchers.IO).launch {
            showDatabase()
        }
        initListener()
    }

    suspend fun uploadDb(onRequestAccessDrive: ((intent: Intent, requestCode: Int) -> Unit)? = null) {
        driveUtils.getDriveService()?.let { driveService ->
            val storageFile = com.google.api.services.drive.model.File()
            storageFile.parents = listOf("appDataFolder")
            storageFile.name = "note"

            val storageFileShm = com.google.api.services.drive.model.File()
            storageFileShm.parents = listOf("appDataFolder")
            storageFileShm.name = "note-shm"

            val storageFileWal = com.google.api.services.drive.model.File()
            storageFileWal.parents = listOf("appDataFolder")
            storageFileWal.name = "note-wal"

            val filePath: File = File(noteDbPath)
            val filePathShm: File = File(noteDbShmPath)
            val filePathWal: File = File(noteDbWal)
            val mediaContent = FileContent("", filePath)
            val mediaContentShm = FileContent("", filePathShm)
            val mediaContentWal = FileContent("", filePathWal)
            try {
                val file: com.google.api.services.drive.model.File = driveService.files().create(storageFile, mediaContent).execute()
                System.out.printf("$TAG Filename: %s File ID: %s \n", file.name, file.id)
                val fileShm: com.google.api.services.drive.model.File =
                    driveService.files().create(storageFileShm, mediaContentShm).execute()
                System.out.printf("$TAG Filename: %s File ID: %s \n", fileShm.name, fileShm.id)
                val fileWal: com.google.api.services.drive.model.File =
                    driveService.files().create(storageFileWal, mediaContentWal).execute()
                System.out.printf("$TAG Filename: %s File ID: %s \n", fileWal.name, fileWal.id)
                withContext(Dispatchers.Main) {
                    showToast("upload db success")
                    binding.tvResult.text = "upload success"
                }
            } catch (e: UserRecoverableAuthIOException) {
                withContext(Dispatchers.Main) {
                    binding.tvResult.text = "upload failed: UserRecoverableAuthIOException"
                    startActivityForResult(e.intent, 1001)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.tvResult.text = "upload failed: ${e.message}"
                }

            } finally {
                withContext(Dispatchers.Main) {
                    binding.progess.gone()
                }
            }
        }
    }

    private suspend fun downloadDb() {
        val driveService = driveUtils.getDriveService()
        driveService?.let { googleDriveService ->
            try {
                withContext(Dispatchers.IO) {
                    kotlin.runCatching {
                        val dir = File("/data/data/com.example.test/databases")
                        if (dir.isDirectory) {
                            val children = dir.list()
                            for (i in children) {
                                File(dir, i).delete()
                            }
                        }
                        val files: FileList = googleDriveService.files().list()
                            .setSpaces("appDataFolder")
                            .setFields("nextPageToken, files(id, name, createdTime)")
                            .setPageSize(10)
                            .execute()
                        if (files.files.size == 0) Log.e("ddddd", "No DB file exists in Drive")
                        for (file in files.files) {
                            System.out.printf(
                                "$TAG Found file: %s (%s) %s\n",
                                file.name, file.id, file.createdTime
                            )
                            if (file.name == "note") {
                                val outputStream: OutputStream = FileOutputStream(noteDbPath)
                                googleDriveService.files().get(file.id).executeMediaAndDownloadTo(outputStream)
                                Log.d(TAG, "downloadDb: $outputStream")
                            } else if (file.name == "note-shm") {
                                val outputStream: OutputStream = FileOutputStream(noteDbShmPath)
                                googleDriveService.files().get(file.id).executeMediaAndDownloadTo(outputStream)
                            } else if (file.name == "note-wal") {
                                val outputStream: OutputStream = FileOutputStream(noteDbWal)
                                googleDriveService.files().get(file.id).executeMediaAndDownloadTo(outputStream)
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    showToast("download DB")
                    binding.tvResult.text = "download success"
                    noteDatabase = DatabaseBuilder.reloadDatabase(applicationContext)
                }
                showDatabase()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "downloadDb exception: ${e.printStackTrace()}")
                    binding.tvResult.text = "download failed ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.progess.gone()
                }
            }
        }
    }

    private fun initListener() {
        binding.btnSign.setOnClickListener {
            signIn()
        }
        binding.btnChoose.setOnClickListener {
            binding.imageSelected.gone()
            selectFile()
        }
        binding.btnSignOut.setOnClickListener {
            signOut()
            binding.tvResult.text = ""
        }
        binding.btnSyn.setOnClickListener {
            uploadDbWithFirebase()
        }
        binding.btnRestore.setOnClickListener {
            downloadDbFromFirebase()
        }
        binding.btnDeleteDb.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                for (i in 1..20) {
                    noteDatabase.noteDao().delete(
                        Note(i, "content$i", "title$i")
                    )
                }
                showDatabase()
            }
        }
        binding.btnInsert.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                for (i in 1..20) {
                    noteDatabase.noteDao().insert(
                        Note(i, "content$i", "title$i")
                    )
                }
//                noteDatabase.noteDao().insert(note2)
                showDatabase()
            }
        }
        binding.btnShowDb.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                showDatabase()
            }
        }
    }

    private fun downloadDbFromFirebase() {
        isDownload = true
        val rootPath = "/data/data/com.example.test/databases"
//        val dir = File(rootPath)
//        if (dir.isDirectory) {
//            val children = dir.list()
//            for (i in children) {
//                if (i.equals("note.db")) {
//                    File(dir, i).delete()
//                }
//            }
//        }
        val localFile = File(rootPath, "note2.db")
        downloadFile(localFile, "db")
    }

    private fun downloadFile(localFile: File, prefix: String) {
        localFile.createNewFile()
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("DataNote")
        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = snapshot.children.find { it.key == idAccount }?.value
                val storageRef = FirebaseStorage.getInstance().reference
                storageRef.child("database/${idAccount}${prefix}").getFile(localFile)
                    .addOnSuccessListener {
                        if (isDownload) {
                            Log.d(TAG, "download DB success: $prefix")
                            binding.tvResult.text = "download success"
                            loadDatabase()
                            binding.progess.hide()
                        }
                    }
                    .addOnFailureListener {
                        showToast("download DB failed")
                        Log.d(TAG, "onFailedDownload: ${it.message}")
                        binding.progess.hide()
                    }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    private fun loadDatabase() {
        binding.progess.show()
        DatabaseBuilder.resetDatabase()
        noteDatabase = DatabaseBuilder.getInstance2(this)
        CoroutineScope(Dispatchers.IO).launch {
            val list = noteDatabase.noteDao().getAll()
            Log.d(TAG, "loadDatabase: ${list?.size}")
            noteDatabase = DatabaseBuilder.reloadDatabase(applicationContext)
            list?.forEach { noteDatabase.noteDao().insert(it) }
            showDatabase()
            withContext(Dispatchers.Main){
                binding.progess.gone()
            }
        }
    }

    var isDownload = false
    private fun uploadDbWithFirebase() {
        isDownload = false
        val storageRef = FirebaseStorage.getInstance().reference
        val fileDb = Uri.fromFile(File(noteDbPath))
        val fileDbRef = storageRef.child("database/${idAccount}db")
        uploadFile(fileDbRef, fileDb)
    }

    private fun uploadFile(fileDbRef: StorageReference, fileDb: Uri) {
        fileDbRef.putFile(fileDb)
            .addOnSuccessListener {
                Log.d(TAG, "uploadFile: $${fileDbRef.name}")
//                showToast("upload success ${fileDbRef.name}")
                fileDbRef.downloadUrl.addOnSuccessListener { uri ->
                    Log.d(TAG, "uploadDbWithFirebase: $uri")
                    saveToFireBase(uri)
                    binding.progess.hide()
                }.addOnFailureListener {
                    binding.progess.hide()
                }
            }.addOnFailureListener {
                showToast("Failed to upload")
                binding.progess.hide()
                Log.d(TAG, "uploadDbWithFirebase: ${it.message}")
            }
    }

    private fun saveToFireBase(uri: Uri?) {
        val database = FirebaseDatabase.getInstance().getReference("DataNote").child(idAccount)
        database.setValue(uri.toString())
    }

    private suspend fun showDatabase() {
        val list = noteDatabase.noteDao().getAll()
        Log.d(TAG, "showDatabase: ${list?.size}")
        withContext(Dispatchers.Main) {
            binding.tvResult.text = list.toString()
        }
        val json = Gson().toJson(list)
        Log.d(TAG, "showDatabase: $json")
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
        idAccount = x.id.toString()
        Picasso.get().load(x.photoUrl).into(binding.imageAvatar)
        binding.imageAvatar.visibility = View.VISIBLE
        binding.tvEmail.text = x.email
        binding.tvName.text = x.displayName
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("DataNote")
        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = snapshot.children.find { it.key == idAccount }?.value
                Log.d(TAG, "data: $result")
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val x = task.getResult(ApiException::class.java)
                setUpView(x)
            } catch (e: Exception) {
                Log.d(TAG, "exception: ${e.printStackTrace()} ")
            }
        }
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_SELECT_FILE -> {
                    val selectedFile = data?.data
                    selectedFile?.let {
                        val tempFile = makeCopy(it)
//                        CoroutineScope(Dispatchers.IO).launch {
//                            getMimeType(it)?.let { it1 ->
//                                driveUtils.uploadFileToGDrive(file = tempFile, it1) { intentRs, requestCode ->
//                                    startActivityForResult(intentRs, requestCode)
//                                }
//                            }
//                        }
                    }
                    Log.d(TAG, "select file ${selectedFile.toString()} ")
                    fileSelected = selectedFile
                    Log.d(TAG, "onActivityResult: ${fileSelected?.let { getMimeType(it) }}")
                    binding.imageSelected.apply {
                        setImageURI(null)
                        setImageURI(fileSelected)
                        show()
                    }
                }
            }
        }
    }

    private var fileSelected: Uri? = null
    private fun selectFile() {
        val intent = Intent()
            .setType("*/*")
            .setAction(Intent.ACTION_GET_CONTENT)
        startActivityForResult(Intent.createChooser(intent, "Select a file"), REQUEST_CODE_SELECT_FILE)
    }

    private fun makeCopy(fileUri: Uri): File {
        val parcelFileDescriptor = contentResolver.openFileDescriptor(fileUri, "r", null)
        val inputStream = FileInputStream(parcelFileDescriptor?.fileDescriptor)
        val file = File(filesDir, getFileName(fileUri))
        val outputStream = FileOutputStream(file)
        IOUtils.copy(inputStream, outputStream)
        return file
    }

    private fun getFileName(uri: Uri): String {
        var name = ""
        val returnCursor = contentResolver.query(uri, null, null, null, null)
        returnCursor?.let {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            name = it.getString(nameIndex)
            it.close()
        }
        return name
    }
}