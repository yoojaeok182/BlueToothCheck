class GaugeViewChart(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val gaugePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 20f
        isAntiAlias = true
    }

    private val needlePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 10f
        isAntiAlias = true
    }

    var value = 0f
        set(value) {
            field = value
            invalidate() // 화면 갱신
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()

        // 게이지 (반원) 그리기
        val rect = RectF(50f, 50f, width - 50f, height + 50f)
        canvas.drawArc(rect, 180f, 180f, false, gaugePaint)

        // 바늘 그리기
        val angle = 180f + (value / 100f) * 180f
        val centerX = width / 2f
        val centerY = height + 50f
        val needleLength = width / 2.5f
        val needleX = (centerX + cos(Math.toRadians(angle.toDouble())) * needleLength).toFloat()
        val needleY = (centerY + sin(Math.toRadians(angle.toDouble())) * needleLength).toFloat()

        canvas.drawLine(centerX, centerY, needleX, needleY, needlePaint)
    }
}
