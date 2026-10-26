package com.example.floatingoverlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * FloatingMenuService:
 * - Uses WindowManager with `TYPE_APPLICATION_OVERLAY` and `FLAG_NOT_FOCUSABLE`.
 * - Renders a Floating Card with dark theme (`#E61E1E2E`).
 * - Top Header is draggable across the screen via raw `MotionEvent` coordinate tracking.
 * - Minimize button (`btn_minimize`) collapses/expands the content body view.
 * - Close button (`btn_close`) stops the service and removes the overlay window.
 * - Tab 1 ("Hướng đi"): Displays Early, Mid, and Late game strategic route recommendations
 *   with interactive playstyle filters.
 * - Tab 2 ("Nâng cấp"): Displays item/stat upgrade priorities sorted by Tier (Tier S, Tier A, Tier B).
 */
class FloatingMenuService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Main Card & Collapsible Body
    private lateinit var floatingCardRoot: LinearLayout
    private lateinit var headerDragBar: LinearLayout
    private lateinit var collapsibleContentBody: LinearLayout
    private lateinit var btnMinimize: TextView
    private lateinit var btnClose: TextView

    // Tabs
    private lateinit var tabBarContainer: LinearLayout
    private lateinit var tabRoute: TextView
    private lateinit var tabUpgrade: TextView
    private lateinit var viewTabRoute: LinearLayout
    private lateinit var viewTabUpgrade: View

    // Tab 1 ("Hướng đi") Views
    private lateinit var btnStyleAttack: TextView
    private lateinit var btnStyleDefense: TextView
    private lateinit var btnStyleSpeed: TextView
    private lateinit var btnStyleEconomy: TextView
    private lateinit var tvEarlyRoute: TextView
    private lateinit var tvMidRoute: TextView
    private lateinit var tvLateRoute: TextView

    // Tab 2 ("Nâng cấp") Container
    private lateinit var upgradeItemsContainer: LinearLayout

    private var isCollapsed = false

    data class RouteGuide(
        val early: String,
        val mid: String,
        val late: String
    )

    data class UpgradePriority(
        val tier: String,
        val badgeColorHex: String,
        val name: String,
        val reason: String
    )

    private val strategyRoutes = mapOf(
        "ATTACK" to RouteGuide(
            early = "• Lên Giày Cuồng Nộ + Kiếm Dài để áp đảo sát thương đường đơn ngay từ cấp 2.",
            mid = "• Hoàn thiện Vô Cực Kiếm + Cung Xanh xuyên giáp. Chủ động kiểm soát Rồng và bắt lẻ chủ lực.",
            late = "• Bổ sung Giáp Thiên Thần + Huyết Kiếm. Giữ vị trí tuyến sau, tối đa hóa sát thương giao tranh tổng."
        ),
        "DEFENSE" to RouteGuide(
            early = "• Khởi đầu Khiên Doran + Giày Thép Gai giảm sát thương vật lý, ưu tiên giữ máu ôm trụ.",
            mid = "• Lên Giáp Lửa + Giáp Tâm Linh tăng máu và hồi phục. Mở giao tranh và che chắn tuyến sau.",
            late = "• Hoàn thiện Giáp Gai + Khiên Băng Randuin. Khép góc xạ thủ địch và hấp thụ sát thương."
        ),
        "SPEED" to RouteGuide(
            early = "• Ưu tiên Rìu Tiamat / Trang bị dọn quái diện rộng. Tối ưu vòng rừng trong 3 phút đầu.",
            mid = "• Đẩy lẻ các làn đường trống kết hợp cướp tài nguyên rừng địch, duy trì khoảng cách +2 cấp độ.",
            late = "• Tận dụng lợi thế cấp 18 sớm để ép Baron và kết thúc ván đấu trước khi địch đủ trang bị."
        ),
        "ECONOMY" to RouteGuide(
            early = "• Khởi đầu trang bị tích vàng + Ngọc hỗ trợ kinh tế. Tập trung last-hit chuẩn xác từng đợt lính.",
            mid = "• Nâng cấp tối đa Mỏ Tinh Thể & Trạm Thu Hoạch cấp 3. Chỉ giao tranh khi có mục tiêu lớn.",
            late = "• Dư dả tài nguyên để xoay chuyển trang bị khắc chế linh hoạt và mua thuốc cường hóa liên tục."
        )
    )

    private val tierUpgrades = listOf(
        UpgradePriority(
            tier = "Tier S",
            badgeColorHex = "#F38BA8",
            name = "Kỹ Năng Dọn Quái Diện Rộng (AoE)",
            reason = "Lý do: Hiệu suất farm cao hơn 45%, giúp tăng tốc độ thu thập vàng và kinh nghiệm đầu trận."
        ),
        UpgradePriority(
            tier = "Tier S",
            badgeColorHex = "#F38BA8",
            name = "Mỏ Tài Nguyên Trung Tâm (Cấp 1 → 5)",
            reason = "Lý do: Chi phí nâng cấp thấp nhưng hoàn vốn nhanh nhất (chỉ sau 4 phút vận hành)."
        ),
        UpgradePriority(
            tier = "Tier A",
            badgeColorHex = "#FAB387",
            name = "Vũ Khí Xuyên Giáp & Nội Tại Tốc Đánh",
            reason = "Lý do: Gia tăng sát thương ổn định lên cả mục tiêu lớn lẫn tướng đỡ đòn ở giữa trận."
        ),
        UpgradePriority(
            tier = "Tier A",
            badgeColorHex = "#FAB387",
            name = "Giày Cơ Động & Giảm Hồi Chiêu",
            reason = "Lý do: Tăng tốc độ đảo đường hỗ trợ đồng đội và giảm 15% thời gian hồi chiêu cuối."
        ),
        UpgradePriority(
            tier = "Tier B",
            badgeColorHex = "#89B4FA",
            name = "Giáp Phản Đòn & Kháng Hiệu Ứng",
            reason = "Lý do: Chỉ phát huy tối đa giá trị ở cuối trận khi đối phương đã có đủ đồ chí mạng."
        ),
        UpgradePriority(
            tier = "Tier B",
            badgeColorHex = "#89B4FA",
            name = "Kho Chứa Dự Trữ Mở Rộng",
            reason = "Lý do: Chưa cần thiết ở giai đoạn đầu vì lượng tài nguyên chưa vượt mức giới hạn kho."
        )
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        initOverlayWindow()
    }

    private fun startForegroundNotification() {
        val channelId = "game_assistant_overlay_channel"
        val channel = NotificationChannel(
            channelId,
            "Game Assistant Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the Game Assistant floating overlay active"
            setShowBadge(false)
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Game Assistant đang chạy")
            .setContentText("Sử dụng thanh tiêu đề để kéo di chuyển hoặc thu gọn menu.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    private fun initOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null)

        // Requirement 3: TYPE_APPLICATION_OVERLAY and FLAG_NOT_FOCUSABLE
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }

        bindAndStyleViews()
        setupHeaderButtonsAndTabs()
        populateUpgradeList()
        setupHeaderDragListener()

        windowManager.addView(floatingView, layoutParams)
    }

    private fun bindAndStyleViews() {
        floatingCardRoot = floatingView.findViewById(R.id.floating_card_root)
        headerDragBar = floatingView.findViewById(R.id.header_drag_bar)
        collapsibleContentBody = floatingView.findViewById(R.id.collapsible_content_body)
        btnMinimize = floatingView.findViewById(R.id.btn_minimize)
        btnClose = floatingView.findViewById(R.id.btn_close)

        tabBarContainer = floatingView.findViewById(R.id.tab_bar_container)
        tabRoute = floatingView.findViewById(R.id.tab_route)
        tabUpgrade = floatingView.findViewById(R.id.tab_upgrade)
        viewTabRoute = floatingView.findViewById(R.id.view_tab_route)
        viewTabUpgrade = floatingView.findViewById(R.id.view_tab_upgrade)

        btnStyleAttack = floatingView.findViewById(R.id.btn_style_attack)
        btnStyleDefense = floatingView.findViewById(R.id.btn_style_defense)
        btnStyleSpeed = floatingView.findViewById(R.id.btn_style_speed)
        btnStyleEconomy = floatingView.findViewById(R.id.btn_style_economy)

        tvEarlyRoute = floatingView.findViewById(R.id.tv_early_route)
        tvMidRoute = floatingView.findViewById(R.id.tv_mid_route)
        tvLateRoute = floatingView.findViewById(R.id.tv_late_route)

        upgradeItemsContainer = floatingView.findViewById(R.id.upgrade_items_container)

        // Requirement 4: Floating Card with dark theme (#E61E1E2E) and rounded corners
        floatingCardRoot.background = createRoundedShape("#E61E1E2E", 14, "#45475A", 1)
        btnMinimize.background = createRoundedShape("#313244", 8, "#45475A", 1)
        btnClose.background = createRoundedShape("#313244", 8, "#45475A", 1)
        tabBarContainer.background = createRoundedShape("#181825", 8)

        floatingView.findViewById<View>(R.id.card_stage_early).background =
            createRoundedShape("#313244", 8)
        floatingView.findViewById<View>(R.id.card_stage_mid).background =
            createRoundedShape("#313244", 8)
        floatingView.findViewById<View>(R.id.card_stage_late).background =
            createRoundedShape("#313244", 8)
    }

    private fun setupHeaderButtonsAndTabs() {
        // Minimize button collapses/expands the content view
        btnMinimize.setOnClickListener {
            isCollapsed = !isCollapsed
            if (isCollapsed) {
                collapsibleContentBody.visibility = View.GONE
                btnMinimize.text = "▢"
            } else {
                collapsibleContentBody.visibility = View.VISIBLE
                btnMinimize.text = "—"
            }
            windowManager.updateViewLayout(floatingView, layoutParams)
        }

        // Close button kills the service and removes the overlay
        btnClose.setOnClickListener {
            stopSelf()
        }

        // Tab switching ("Hướng đi" vs "Nâng cấp")
        tabRoute.setOnClickListener { selectTab(isRouteTab = true) }
        tabUpgrade.setOnClickListener { selectTab(isRouteTab = false) }

        // Playstyle route buttons
        btnStyleAttack.setOnClickListener { selectPlaystyle("ATTACK", btnStyleAttack) }
        btnStyleDefense.setOnClickListener { selectPlaystyle("DEFENSE", btnStyleDefense) }
        btnStyleSpeed.setOnClickListener { selectPlaystyle("SPEED", btnStyleSpeed) }
        btnStyleEconomy.setOnClickListener { selectPlaystyle("ECONOMY", btnStyleEconomy) }

        selectTab(isRouteTab = true)
        selectPlaystyle("ATTACK", btnStyleAttack)
    }

    private fun selectTab(isRouteTab: Boolean) {
        viewTabRoute.visibility = if (isRouteTab) View.VISIBLE else View.GONE
        viewTabUpgrade.visibility = if (isRouteTab) View.GONE else View.VISIBLE

        if (isRouteTab) {
            tabRoute.background = createRoundedShape("#89B4FA", 6)
            tabRoute.setTextColor(Color.parseColor("#1E1E2E"))
            tabUpgrade.background = createRoundedShape("#00000000", 6)
            tabUpgrade.setTextColor(Color.parseColor("#A6ADC8"))
        } else {
            tabUpgrade.background = createRoundedShape("#89B4FA", 6)
            tabUpgrade.setTextColor(Color.parseColor("#1E1E2E"))
            tabRoute.background = createRoundedShape("#00000000", 6)
            tabRoute.setTextColor(Color.parseColor("#A6ADC8"))
        }
    }

    private fun selectPlaystyle(styleKey: String, activeBtn: TextView) {
        val buttons = listOf(btnStyleAttack, btnStyleDefense, btnStyleSpeed, btnStyleEconomy)
        for (btn in buttons) {
            if (btn == activeBtn) {
                btn.background = createRoundedShape("#313244", 8, "#89B4FA", 1)
                btn.setTextColor(Color.parseColor("#89B4FA"))
                btn.setTypeface(null, Typeface.BOLD)
            } else {
                btn.background = createRoundedShape("#181825", 8, "#45475A", 1)
                btn.setTextColor(Color.parseColor("#CDD6F4"))
                btn.setTypeface(null, Typeface.NORMAL)
            }
        }

        strategyRoutes[styleKey]?.let { guide ->
            tvEarlyRoute.text = guide.early
            tvMidRoute.text = guide.mid
            tvLateRoute.text = guide.late
        }
    }

    private fun populateUpgradeList() {
        upgradeItemsContainer.removeAllViews()

        for (item in tierUpgrades) {
            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedShape("#313244", 8)
                val pad = dpToPx(8)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(6)
                }
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val badge = TextView(this).apply {
                text = item.tier
                setTextColor(Color.parseColor("#1E1E2E"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTypeface(null, Typeface.BOLD)
                background = createRoundedShape(item.badgeColorHex, 4)
                setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            }

            val title = TextView(this).apply {
                text = item.name
                setTextColor(Color.parseColor("#CDD6F4"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTypeface(null, Typeface.BOLD)
                setPadding(dpToPx(8), 0, 0, 0)
            }

            topRow.addView(badge)
            topRow.addView(title)

            val reason = TextView(this).apply {
                text = item.reason
                setTextColor(Color.parseColor("#BAC2DE"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dpToPx(4), 0, 0)
            }

            itemCard.addView(topRow)
            itemCard.addView(reason)
            upgradeItemsContainer.addView(itemCard)
        }
    }

    /**
     * Requirement 4: Top Header draggable across the screen via MotionEvent tracking.
     * Uses raw screen coordinates (`event.rawX` and `event.rawY`) for zero-lag dragging.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupHeaderDragListener() {
        val touchSlop = dpToPx(4)

        headerDragBar.setOnTouchListener(object : View.OnTouchListener {
            private var initialWindowX = 0
            private var initialWindowY = 0
            private var initialTouchRawX = 0f
            private var initialTouchRawY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWindowX = layoutParams.x
                        initialWindowY = layoutParams.y
                        initialTouchRawX = event.rawX
                        initialTouchRawY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchRawX).toInt()
                        val deltaY = (event.rawY - initialTouchRawY).toInt()

                        if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                            layoutParams.x = initialWindowX + deltaX
                            layoutParams.y = initialWindowY + deltaY
                            windowManager.updateViewLayout(floatingView, layoutParams)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun createRoundedShape(
        fillHex: String,
        radiusDp: Int,
        strokeHex: String? = null,
        strokeWidthDp: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null && strokeWidthDp > 0) {
                setStroke(dpToPx(strokeWidthDp), Color.parseColor(strokeHex))
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
    }
}
