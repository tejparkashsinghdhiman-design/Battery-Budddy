package com.example.batterybuddy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.RemoteViews

/** Home-screen widget: a snapshot of the buddy, the battery level and her mood. */
class BuddyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        push(context, appWidgetManager, appWidgetIds, BuddyState.snapshot(context))
    }

    companion object {
        /** Called by the service (at most once a minute) so the widget follows the battery. */
        fun updateAll(ctx: Context, st: BuddyState) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, BuddyWidget::class.java))
            if (ids.isNotEmpty()) push(ctx, mgr, ids, st)
        }

        private fun push(ctx: Context, mgr: AppWidgetManager, ids: IntArray, st: BuddyState) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_buddy)
            views.setImageViewBitmap(R.id.w_img, render(ctx, st, 240, 300))
            views.setTextViewText(R.id.w_title, "${st.level}%" + (if (st.charging) " \u26a1" else ""))
            views.setTextViewText(R.id.w_sub, Moods.describe(st))
            views.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            mgr.updateAppWidget(ids, views)
        }

        /** Draws one frame of the real BuddyView into a bitmap. */
        private fun render(ctx: Context, st: BuddyState, w: Int, h: Int): Bitmap {
            val v = BuddyView(ctx.applicationContext)
            v.state = st
            v.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            v.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            return bmp
        }
    }
}
