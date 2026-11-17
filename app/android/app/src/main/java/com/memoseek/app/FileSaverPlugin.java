package com.memoseek.app;

import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;
import java.util.Base64;

/**
 * 导出历史对话的 ZIP：走系统「保存文件」对话框。
 *
 * 背景：WebView 里用 <a download> 触发 blob 下载在 Android 上完全无效，
 * 而 Capacitor Filesystem 的目录落在应用私有位置，用户在系统文件选择器里
 * 看不到、也无法再导入回来。这里改用 Storage Access Framework 的
 * ACTION_CREATE_DOCUMENT，由系统弹出保存对话框，用户自选位置，
 * 落盘后还能原样导回本应用。
 */
@CapacitorPlugin(name = "FileSaver")
public class FileSaverPlugin extends Plugin {

  @PluginMethod
  public void save(PluginCall call) {
    String name = call.getString("name");
    String base64 = call.getString("data");
    if (name == null || base64 == null) {
      call.reject("缺少文件名或文件内容");
      return;
    }
    // base64 体积大，单独存字段，避免经过 Intent 传参
    pendingBase64 = base64;

    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("application/zip");
    intent.putExtra(Intent.EXTRA_TITLE, name);
    startActivityForResult(call, intent, "saveResult");
  }

  /** 待写入的 base64 内容；保存对话框返回时用它落盘 */
  private String pendingBase64 = "";

  @ActivityCallback
  private void saveResult(PluginCall call, ActivityResult result) {
    if (call == null) {
      return;
    }
    if (result.getResultCode() != android.app.Activity.RESULT_OK
        || result.getData() == null
        || result.getData().getData() == null) {
      call.reject("用户取消了保存", "CANCELLED");
      return;
    }
    Uri uri = result.getData().getData();
    String base64 = pendingBase64;
    pendingBase64 = "";
    if (base64 == null || base64.isEmpty()) {
      call.reject("导出数据丢失");
      return;
    }
    try (OutputStream output = getContext().getContentResolver().openOutputStream(uri)) {
      if (output == null) {
        call.reject("无法写入所选位置");
        return;
      }
      output.write(Base64.getDecoder().decode(base64));
      JSObject payload = new JSObject();
      payload.put("uri", uri.toString());
      payload.put("name", displayNameOf(uri));
      call.resolve(payload);
    } catch (Exception error) {
      call.reject("写入文件失败: " + error.getMessage());
    }
  }

  /** 读取用户所选文件的显示名；读不到时退回通用名 */
  private String displayNameOf(Uri uri) {
    try (android.database.Cursor cursor =
        getContext()
            .getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (index >= 0 && cursor.getString(index) != null) {
          return cursor.getString(index);
        }
      }
    } catch (Exception ignored) {
      // 某些提供方不支持查询，忽略即可
    }
    return "export.zip";
  }
}
