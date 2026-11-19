package com.thehhr.form.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException

/**
 * The user-picked vault folder (Storage Access Framework).
 * Uses the same SharedPreferences ("saf_vault"/"tree_uri") as the web app's SafVaultPlugin, so an
 * in-place upgrade keeps the folder grant and nothing needs re-picking.
 */
class SafVault(private val context: Context) {
    private val prefs = context.getSharedPreferences("saf_vault", Context.MODE_PRIVATE)
    private val resolver get() = context.contentResolver

    val treeUri: Uri? get() = prefs.getString("tree_uri", null)?.takeIf { it.isNotEmpty() }?.let(Uri::parse)

    fun hasGrant(uri: Uri? = treeUri): Boolean = uri != null && resolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission && it.isWritePermission
    }

    fun folderName(uri: Uri? = treeUri): String = uri?.let {
        query(rootDoc(it), DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    } ?: ""

    /** Called with the OpenDocumentTree result. Releases the previous grant (the OS caps persisted grants). */
    fun adopt(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(uri, flags)
        val old = treeUri
        prefs.edit().putString("tree_uri", uri.toString()).apply()
        if (old != null && old != uri) runCatching { resolver.releasePersistableUriPermission(old, flags) }
    }

    private fun rootDoc(tree: Uri) = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun query(doc: Uri, column: String): String? = runCatching {
        resolver.query(doc, arrayOf(column), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** fix: "not found" (null) and "couldn't list" (throws) are different; the web plugin treated both as missing and forked files. */
    private fun findChild(tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cursor = resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?: throw IOException("Can't list vault folder")
        cursor.use { c ->
            while (c.moveToNext()) if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
        }
        return null
    }

    private fun requireTree(): Uri {
        val tree = treeUri ?: throw IOException("No vault folder selected")
        if (!hasGrant(tree)) throw IOException("permission_lost")
        return tree
    }

    /** File text, or null when the file doesn't exist. */
    fun read(name: String): String? {
        val tree = requireTree()
        val doc = findChild(tree, name) ?: return null
        val stream = resolver.openInputStream(doc) ?: throw IOException("Can't open $name")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun write(name: String, text: String) {
        val tree = requireTree()
        val doc = findChild(tree, name)
            ?: DocumentsContract.createDocument(resolver, rootDoc(tree), "text/markdown", name)
            ?: throw IOException("Can't create $name")
        val out = resolver.openOutputStream(doc, "wt") ?: throw IOException("Can't write $name")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() }
    }

    /** Copy of a file's previous content in app storage before the first overwrite of a session. */
    fun backup(name: String, text: String) {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        File(dir, "${System.currentTimeMillis()}-$name").writeText(text)
        // keep the newest 10 per file
        dir.listFiles { f -> f.name.endsWith("-$name") }?.sortedByDescending { it.name }?.drop(10)?.forEach { it.delete() }
    }
}
