package com.thehhr.form;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SAF bridge for the markdown vault: lets the user pick a real folder via
 * ACTION_OPEN_DOCUMENT_TREE and reads/writes the 5 vault .md files at the
 * root of that tree. The picked grant is persisted so it survives reboots.
 */
@CapacitorPlugin(name = "SafVault")
public class SafVaultPlugin extends Plugin {

  private static final String PREFS_NAME = "saf_vault";
  private static final String KEY_TREE_URI = "tree_uri";
  private static final int GRANT_FLAGS =
      Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

  @PluginMethod
  public void pickFolder(PluginCall call) {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    Uri current = storedTreeUri();
    if (current != null) {
      intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, current);
    }
    startActivityForResult(call, intent, "onPickResult");
  }

  @ActivityCallback
  private void onPickResult(PluginCall call, ActivityResult result) {
    if (call == null) return;
    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
      call.reject("cancelled");
      return;
    }
    Uri treeUri = result.getData().getData();
    if (treeUri == null) {
      call.reject("cancelled");
      return;
    }
    try {
      getContext().getContentResolver().takePersistableUriPermission(treeUri, GRANT_FLAGS);
    } catch (SecurityException e) {
      call.reject("takePersistableUriPermission failed: " + e.getMessage());
      return;
    }
    /* Previous grants are intentionally kept (128-grant cap) so data can be
       copied out of an older tree during backend migration. */
    prefs().edit().putString(KEY_TREE_URI, treeUri.toString()).apply();
    JSObject out = new JSObject();
    out.put("uri", treeUri.toString());
    out.put("name", rootDisplayName(treeUri));
    call.resolve(out);
  }

  @PluginMethod
  public void getFolder(PluginCall call) {
    JSObject out = new JSObject();
    Uri treeUri = storedTreeUri();
    if (treeUri == null || !hasGrant(treeUri)) {
      out.put("valid", false);
      call.resolve(out);
      return;
    }
    out.put("valid", true);
    out.put("uri", treeUri.toString());
    out.put("name", rootDisplayName(treeUri));
    call.resolve(out);
  }

  @PluginMethod
  public void clearFolder(PluginCall call) {
    Uri treeUri = storedTreeUri();
    if (treeUri != null) {
      try {
        getContext().getContentResolver().releasePersistableUriPermission(treeUri, GRANT_FLAGS);
      } catch (SecurityException ignored) {
      }
      prefs().edit().remove(KEY_TREE_URI).apply();
    }
    call.resolve();
  }

  @PluginMethod
  public void readFile(PluginCall call) {
    String name = call.getString("name", "");
    if (name.isEmpty()) {
      call.reject("name is required");
      return;
    }
    String uriOverride = call.getString("uri", "");
    Uri treeUri = requireTree(call, uriOverride);
    if (treeUri == null) return;
    try {
      ContentResolver resolver = getContext().getContentResolver();
      Uri child = findChild(resolver, treeUri, name);
      JSObject out = new JSObject();
      if (child != null) {
        String text = readText(resolver, child);
        if (text != null) out.put("data", text);
      }
      call.resolve(out);
    } catch (Exception e) {
      call.reject("read failed: " + e.getMessage());
    }
  }

  @PluginMethod
  public void writeFile(PluginCall call) {
    String name = call.getString("name", "");
    String data = call.getString("data", "");
    if (name.isEmpty()) {
      call.reject("name is required");
      return;
    }
    String uriOverride = call.getString("uri", "");
    Uri treeUri = requireTree(call, uriOverride);
    if (treeUri == null) return;
    try {
      ContentResolver resolver = getContext().getContentResolver();
      Uri child = findChild(resolver, treeUri, name);
      if (child == null) {
        child = createChild(resolver, rootDocumentUri(treeUri), name);
      }
      if (child == null) throw new IOException("createDocument returned null");
      writeText(resolver, child, data);
      JSObject out = new JSObject();
      out.put("saved", true);
      call.resolve(out);
    } catch (Exception e) {
      call.reject("write failed: " + e.getMessage());
    }
  }

  /* --- helpers --- */

  private Uri requireTree(PluginCall call, String uriOverride) {
    Uri treeUri;
    if (uriOverride != null && !uriOverride.isEmpty()) {
      treeUri = Uri.parse(uriOverride);
    } else {
      treeUri = storedTreeUri();
      if (treeUri != null && !hasGrant(treeUri)) {
        call.reject("permission_lost");
        return null;
      }
    }
    if (treeUri == null) {
      call.reject("permission_lost");
      return null;
    }
    return treeUri;
  }

  private SharedPreferences prefs() {
    return getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  private Uri storedTreeUri() {
    String raw = prefs().getString(KEY_TREE_URI, null);
    if (raw == null || raw.isEmpty()) return null;
    return Uri.parse(raw);
  }

  private boolean hasGrant(Uri treeUri) {
    try {
      for (UriPermission grant : getContext().getContentResolver().getPersistedUriPermissions()) {
        if (treeUri.equals(grant.getUri()) && grant.isReadPermission() && grant.isWritePermission()) return true;
      }
    } catch (SecurityException ignored) {
    }
    return false;
  }

  private Uri rootDocumentUri(Uri treeUri) {
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
  }

  private String rootDisplayName(Uri treeUri) {
    return queryDisplayName(getContext().getContentResolver(), rootDocumentUri(treeUri));
  }

  private String queryDisplayName(ContentResolver resolver, Uri documentUri) {
    Cursor cursor = null;
    try {
      cursor = resolver.query(documentUri,
          new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
      if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
    } catch (Exception ignored) {
    } finally {
      if (cursor != null) cursor.close();
    }
    return "";
  }

  /* Display-name iteration instead of a selection query: some providers
     ignore selection args, and this keeps writes dup-safe ("file (1).md"). */
  private Uri findChild(ContentResolver resolver, Uri treeUri, String displayName) {
    Cursor cursor = null;
    try {
      Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
          treeUri, DocumentsContract.getTreeDocumentId(treeUri));
      cursor = resolver.query(childrenUri,
          new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
              DocumentsContract.Document.COLUMN_DISPLAY_NAME},
          null, null, null);
      if (cursor == null) return null;
      while (cursor.moveToNext()) {
        String docId = cursor.getString(0);
        String name = cursor.getString(1);
        if (displayName.equals(name)) {
          return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
        }
      }
      return null;
    } catch (Exception e) {
      return null;
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  private Uri createChild(ContentResolver resolver, Uri parent, String name) {
    try {
      return DocumentsContract.createDocument(resolver, parent, "text/markdown", name);
    } catch (Exception e) {
      try {
        return DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name);
      } catch (Exception ignored) {
        return null;
      }
    }
  }

  private String readText(ContentResolver resolver, Uri documentUri) throws IOException {
    InputStream in = resolver.openInputStream(documentUri);
    if (in == null) throw new IOException("openInputStream returned null");
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[16384];
      int read;
      while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
      return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    } finally {
      try { in.close(); } catch (IOException ignored) {
      }
    }
  }

  private void writeText(ContentResolver resolver, Uri documentUri, String data) throws IOException {
    OutputStream out;
    try {
      out = resolver.openOutputStream(documentUri, "wt");
    } catch (IOException e) {
      out = null;
    }
    if (out == null) out = resolver.openOutputStream(documentUri);
    if (out == null) throw new IOException("openOutputStream returned null");
    try {
      out.write(data.getBytes(StandardCharsets.UTF_8));
      out.flush();
    } finally {
      try { out.close(); } catch (IOException ignored) {
      }
    }
  }
}
