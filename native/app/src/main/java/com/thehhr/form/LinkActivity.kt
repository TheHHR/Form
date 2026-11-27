package com.thehhr.form

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Invisible entry point for shortcuts, notifications, the widget and the tile. It applies the action, then brings
 * up MainActivity with a plain launch intent. MainActivity's task therefore never starts from an action intent, so
 * Android can't replay an old action (e.g. "Log food") when it later recreates the task after process death.
 */
class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivity.handleAction(FormStore.get(this), intent)
        startActivity(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        fun intent(ctx: Context, action: String?) = Intent(ctx, LinkActivity::class.java).setAction(action ?: Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
