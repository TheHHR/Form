package com.thehhr.form;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(SafVaultPlugin.class);
    super.onCreate(savedInstanceState);
    View container = (View) bridge.getWebView().getParent();
    container.setBackgroundColor(Color.BLACK);
    ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
      Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
      v.setPadding(bars.left, bars.top, bars.right, keyboardVisible ? ime.bottom : bars.bottom);
      return new WindowInsetsCompat.Builder(insets)
          .setInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 0, 0, 0))
          .build();
    });
  }
}
