package com.memoseek.app;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

/**
 * 主 Activity。
 *
 * Android 15 起，targetSdk 35+ 的应用被强制 edge-to-edge：WebView 会铺满整块屏幕，
 * 顶部内容直接压在状态栏下面。Capacitor 本身不做 insets 处理，
 * 而 WebView 的 env(safe-area-inset-*) 在部分 ROM 上取不到值（实测本机返回 0），
 * 因此这里把系统栏的实际高度以 CSS 变量注入页面，由样式表统一消费。
 *
 * 注入的变量：
 *   --safe-top    状态栏 + 刘海高度
 *   --safe-bottom 导航栏 / 手势条高度
 *   --safe-left   左侧刘海 / 打孔宽度（横屏时尤其重要）
 *   --safe-right  右侧刘海 / 打孔宽度
 */
public class MainActivity extends BridgeActivity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // 注册导出用的原生插件：系统「保存文件」对话框
    registerPlugin(FileSaverPlugin.class);

    // 页面每次加载完成后重新下发一次，避免导航重建 documentElement 的内联样式
    getBridge()
        .addWebViewListener(
            new WebViewListener() {
              @Override
              public void onPageLoaded(WebView webView) {
                View content = findViewById(android.R.id.content);
                if (content != null) {
                  ViewCompat.requestApplyInsets(content);
                }
              }
            });

    View content = findViewById(android.R.id.content);
    if (content != null) {
      ViewCompat.setOnApplyWindowInsetsListener(
          content,
          (view, windowInsets) -> {
            applySafeArea(windowInsets);
            // 不消费 insets，交由子视图继续按默认逻辑处理
            return windowInsets;
          });
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    View content = findViewById(android.R.id.content);
    if (content != null) {
      ViewCompat.requestApplyInsets(content);
    }
  }

  private void applySafeArea(WindowInsetsCompat windowInsets) {
    if (getBridge() == null) {
      return;
    }
    WebView webView = getBridge().getWebView();
    if (webView == null) {
      return;
    }
    Insets bars =
        windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
    float density = getResources().getDisplayMetrics().density;
    if (density <= 0f) {
      return;
    }
    String script =
        String.format(
            "(function(){var s=document.documentElement.style;"
                + "s.setProperty('--safe-top','%.1fpx');"
                + "s.setProperty('--safe-bottom','%.1fpx');"
                + "s.setProperty('--safe-left','%.1fpx');"
                + "s.setProperty('--safe-right','%.1fpx');})();",
            bars.top / density,
            bars.bottom / density,
            bars.left / density,
            bars.right / density);
    webView.post(() -> webView.evaluateJavascript(script, null));
  }
}
