package com.hy300.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * Launcher leve para o projetor HY300: sem AndroidX, sem Compose, sem imagens remotas.
 * Grade de apps navegavel pelo controle remoto (setas + OK).
 */
class MainActivity : Activity() {

    private class AppItem(
        val key: String,
        val label: String,
        val intent: Intent,
        val resolveInfo: ResolveInfo?,
        val fallbackIconRes: Int = 0
    )

    private class Holder(val icon: ImageView, val label: TextView)

    // Pacotes que voce nao quer ver na grade (opcional). Ex.: setOf("com.exemplo.app")
    private val hiddenPackages = setOf<String>()

    private lateinit var grid: GridView
    private val appAdapter = AppAdapter()
    private val iconCache = HashMap<String, Drawable>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#0E1116")))
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        appAdapter.items = loadApps()
        appAdapter.notifyDataSetChanged()
        grid.requestFocus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Apertar HOME dentro do launcher volta para o primeiro item.
        grid.setSelection(0)
    }

    // Um launcher nao deve fechar com o botao voltar.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Início"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(
            title,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val clockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val time = TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            gravity = Gravity.END
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d 'de' MMMM"
            format24Hour = "EEEE, d 'de' MMMM"
            setTextColor(Color.parseColor("#9AA4B2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.END
        }
        clockBox.addView(
            time,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        clockBox.addView(
            date,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        header.addView(
            clockBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val headerParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        headerParams.bottomMargin = dp(20)
        root.addView(header, headerParams)

        grid = GridView(this).apply {
            numColumns = 6
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = dp(8)
            verticalSpacing = dp(8)
            setSelector(highlightDrawable())
            setDrawSelectorOnTop(false)
            isFocusable = true
            clipToPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
            adapter = appAdapter
            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                launch(appAdapter.items[position])
            }
        }
        root.addView(
            grid,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        return root
    }

    private fun highlightDrawable(): Drawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(14).toFloat()
        d.setColor(Color.parseColor("#26FFFFFF"))
        d.setStroke(dp(3), Color.parseColor("#4FC3F7"))
        return d
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val found = LinkedHashMap<String, AppItem>()
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName) continue
                if (pkg in hiddenPackages) continue
                if (found.containsKey(pkg)) continue
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                found[pkg] = AppItem(pkg, ri.loadLabel(pm).toString(), launch, ri)
            }
        }
        val sorted = found.values.sortedBy { it.label.lowercase() }

        val result = ArrayList<AppItem>()
        // Atalho fixo para as Configuracoes, para voce nunca ficar preso.
        result.add(
            AppItem(
                "__settings__",
                "Configurações",
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                null,
                android.R.drawable.ic_menu_preferences
            )
        )
        result.addAll(sorted)
        return result
    }

    private fun iconFor(item: AppItem): Drawable {
        iconCache[item.key]?.let { return it }
        val ri = item.resolveInfo
        val drawable: Drawable = try {
            if (ri != null) {
                ri.loadIcon(packageManager)
            } else if (item.fallbackIconRes != 0) {
                getDrawable(item.fallbackIconRes) ?: packageManager.defaultActivityIcon
            } else {
                packageManager.defaultActivityIcon
            }
        } catch (e: Exception) {
            packageManager.defaultActivityIcon
        }
        iconCache[item.key] = drawable
        return drawable
    }

    private fun launch(item: AppItem) {
        try {
            startActivity(item.intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir ${item.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private inner class AppAdapter : BaseAdapter() {
        var items: List<AppItem> = emptyList()

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell: View
            val holder: Holder
            if (convertView == null) {
                val icon = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                val label = TextView(this@MainActivity).apply {
                    setTextColor(Color.parseColor("#E6EDF3"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                val labelParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                labelParams.topMargin = dp(10)

                val box = LinearLayout(this@MainActivity)
                box.orientation = LinearLayout.VERTICAL
                box.gravity = Gravity.CENTER
                box.setPadding(dp(6), dp(14), dp(6), dp(10))
                box.addView(icon, LinearLayout.LayoutParams(dp(72), dp(72)))
                box.addView(label, labelParams)
                box.layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(150)
                )
                holder = Holder(icon, label)
                box.tag = holder
                cell = box
            } else {
                cell = convertView
                holder = convertView.tag as Holder
            }
            val item = items[position]
            holder.icon.setImageDrawable(iconFor(item))
            holder.label.text = item.label
            return cell
        }
    }
}
