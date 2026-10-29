package br.com.mi.atv.launcher

import androidx.compose.ui.graphics.Color

data class HeroPalette(
    val primary: Color,
    val secondary: Color,
    val accent: Color
)

object Aurora {
    private val palettes = listOf(
        HeroPalette(Color(0xFF5A3E42), Color(0xFF28191C), Color(0xFFD4A59A)),
        HeroPalette(Color(0xFF1F3044), Color(0xFF0D1520), Color(0xFF64B5F6)),
        HeroPalette(Color(0xFF2E1D3A), Color(0xFF120B18), Color(0xFFBA68C8)),
        HeroPalette(Color(0xFF1B3B2B), Color(0xFF0A1912), Color(0xFF81C784)),
        HeroPalette(Color(0xFF4A2511), Color(0xFF1D0E06), Color(0xFFFFB74D))
    )

    fun forKey(key: String): HeroPalette {
        if (key.isEmpty()) return palettes[0]
        val idx = (key.hashCode() and 0x7FFFFFFF) % palettes.size
        return palettes[idx]
    }
}
