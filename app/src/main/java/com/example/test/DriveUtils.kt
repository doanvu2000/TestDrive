package com.example.test

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class DriveUtils(val context: Context, val sharedPref: SharedPreferences) {
    companion object {
        const val FOLDER = "application/vnd.google-apps.folder"
        const val GOOGLE_DRIVE_FOLDER_ID = "drive_folder_id"
    }

    fun getDriveService(): Drive? {
        GoogleSignIn.getLastSignedInAccount(context)?.let { googleAccount ->
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE, DriveScopes.DRIVE_APPDATA)
            )
            credential.selectedAccount = googleAccount.account
            return Drive.Builder(
                NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
            ).setApplicationName("TestDrive")
                .build()
        }

        return null
    }

    fun createFolder(): String? {
        val googleDrive = getDriveService()
        var folderId: String? = null
        try {
            if (googleDrive != null) {
                val folderData = com.google.api.services.drive.model.File()
                folderData.name = "TestDriveApi"
                folderData.mimeType = FOLDER
                val folder = googleDrive.files().create(folderData).execute()
                Log.d("ddddd", "Folder Id: $folder")
                folderId = folder.id
                sharedPref.edit(true) { putString(GOOGLE_DRIVE_FOLDER_ID, folderId) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return folderId
    }

    fun searchFile(
        fileName: String? = null,
        mimeType: String? = null,
        folderId: String? = null
    ): List<com.google.api.services.drive.model.File>? {
        Log.d("ddddd", "Searching : $fileName")
        var fileList: List<com.google.api.services.drive.model.File>? = null
        try {
            getDriveService()?.let { driveService ->
                var pageToken: String? = null
                val request = driveService.files().list().apply {
                    fields = "nextPageToken, files(id,name)"
                    pageToken = this.pageToken
                }
                var query: String? = null
                fileName?.let {
                    query = "name=\"$fileName\""
                }
                mimeType?.let {
                    query = if (query == null) {
                        "mimeType='$it'"
                    } else "$query and mimeType = '$it'"
                }
                folderId?.let {
                    request.spaces = it
                }
                request.q = query
                val result = request.execute()
                fileList = result.files
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Log.d("ddddd", "File found: ${fileList?.size}")
        return fileList
    }

    fun uploadFileToGDrive(
        file: File,
        type: String,
        folderId: String? = null,
        onUserRecoverableAuthIOException: ((intent: Intent, requestCode: Int) -> Unit)? = null
    ): com.google.api.services.drive.model.File? {
        getDriveService()?.let { googleDriveService ->
            try {
                val gfile = com.google.api.services.drive.model.File()
                gfile.name = file.name
                folderId?.let {
                    gfile.parents = listOf(it)
                }
                val fileContent = FileContent(type, file)
                val result = googleDriveService.Files().create(gfile, fileContent).execute()
                Log.d("ddddd", "upload success -  name:${result.name}, id: ${result.id}")
                return result
            } catch (ex: UserRecoverableAuthIOException) {
                onUserRecoverableAuthIOException?.invoke(ex.intent, MainActivity.REQUEST_AUTHORIZATION)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } ?: Log.d("ddddd", "Signin error - not logged in")
        return null
    }

    fun uploadFileToGDrive2(
        file: File,
        type: String,
        folderId: String? = null
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            getDriveService()?.let { googleDriveService ->
                try {
                    val gfile = com.google.api.services.drive.model.File()
                    gfile.name = file.name
                    folderId?.let {
                        gfile.parents = listOf(it)
                    }
                    val fileContent = FileContent(type, file)
                    val result = googleDriveService.Files().create(gfile, fileContent).execute()
                    Log.d("ddddd", "upload success -  name:${result.name}, id: ${result.id}")
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } ?: Log.d("ddddd", "Signin error - not logged in")
        }
    }

    fun deleteFile(fileId: String) {
        try {
            getDriveService()?.files()?.delete(fileId)?.execute()
            Log.d("ddddd", "Delete file : $fileId")
        } catch (e: Exception) {
            Log.d("ddddd", "Exception during delete : $e")
        }
    }

    fun downloadFileFromGDrive(fileName: String): ByteArrayOutputStream? {
        val listFile = searchFile(fileName, "image/png")
        val driveService = getDriveService()
        listFile?.forEach { file ->
            try {
                val outputStream = ByteArrayOutputStream()
                driveService?.let {
                    it.files()?.get(file.id)?.executeMediaAndDownloadTo(outputStream)
                    Log.d("dddd", "downloadFileFromGDrive: $outputStream")
                    return outputStream
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Log.d("dddd", "downloadFileFromGDrive: null")
        return null
    }

    fun downloadAndSync(file: com.google.api.services.drive.model.File): FileOutputStream? {
        val outputStream = downloadFileFromGDrive(file.id)
        outputStream?.let { bos ->
            val folderName = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val localFile = File(folderName, file.name)
            val fileOutputStream = FileOutputStream(localFile)
            try {
                bos.writeTo(fileOutputStream)
                Log.d("dddd", "downloadAndSync: $fileOutputStream")
                return fileOutputStream
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Log.d("dddd", "downloadAndSync: null")
        return null
    }

    suspend fun accessDriveFiles() {
        withContext(Dispatchers.IO) {
            Log.d("ddddd", "accessDriveFiles")
            getDriveService()?.let { googleDriveService ->
                var pageToken: String? = null
                do {
                    val result = googleDriveService.files().list().apply {
                        fields = "nextPageToken, files(id,name)"
                        pageToken = this.pageToken
                        spaces = spaces
                    }.execute()
                    Log.d("ddddd", "Result ${result.files.size}")
                    for (file in result.files) {
                        Log.d("ddddd", "name = ${file.name} id = ${file.id}")
                    }
                } while (pageToken != null)
            }
        }
    }
}