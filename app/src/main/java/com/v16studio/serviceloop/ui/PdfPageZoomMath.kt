package com.v16studio.serviceloop.ui

internal data class PdfPageTransform(
    val scale: Float = MIN_PDF_PAGE_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    companion object {
        const val MIN_PDF_PAGE_SCALE = 1f
        const val MAX_PDF_PAGE_SCALE = 4f
    }
}

internal data class PdfPagePanBounds(val horizontal: Float, val vertical: Float)

internal object PdfPageZoomMath {
    fun clampScale(scale: Float): Float = scale.coerceIn(
        PdfPageTransform.MIN_PDF_PAGE_SCALE,
        PdfPageTransform.MAX_PDF_PAGE_SCALE,
    )

    fun translationBounds(
        displayedPageWidth: Float,
        displayedPageHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        scale: Float,
    ): PdfPagePanBounds {
        if (scale <= PdfPageTransform.MIN_PDF_PAGE_SCALE) return PdfPagePanBounds(0f, 0f)
        val safeScale = clampScale(scale)
        return PdfPagePanBounds(
            horizontal = ((displayedPageWidth * safeScale - viewportWidth) / 2f).coerceAtLeast(0f),
            vertical = ((displayedPageHeight * safeScale - viewportHeight) / 2f).coerceAtLeast(0f),
        )
    }

    fun clampTranslation(
        offsetX: Float,
        offsetY: Float,
        bounds: PdfPagePanBounds,
        scale: Float,
    ): PdfPageTransform {
        val safeScale = clampScale(scale)
        if (safeScale == PdfPageTransform.MIN_PDF_PAGE_SCALE) return reset()
        return PdfPageTransform(
            scale = safeScale,
            offsetX = offsetX.coerceIn(-bounds.horizontal, bounds.horizontal),
            offsetY = offsetY.coerceIn(-bounds.vertical, bounds.vertical),
        )
    }

    fun reset(): PdfPageTransform = PdfPageTransform()
}
