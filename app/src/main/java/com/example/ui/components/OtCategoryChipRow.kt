package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OtCategoryChipRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        Triple("TODOS", "Todos", Icons.Default.AllInclusive),
        Triple("ALERTAS", "Alertas Vencimiento", Icons.Default.Warning),
        Triple("manto", "Manto y Calderería", Icons.Default.Engineering),
        Triple("eje", "Ejes Mecanizados", Icons.Default.Build),
        Triple("poleas", "Poleas Completas", Icons.Default.Settings),
        Triple("sellos", "Sellos de Agua", Icons.Default.WaterDrop),
        Triple("armado_taller", "Planos de Armado", Icons.Default.Architecture)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { (code, label, icon) ->
            val isSelected = selectedCategory.equals(code, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(code) },
                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (code == "ALERTAS") Color(0xFFEA580C) else if (isSelected) Color.White else Color(0xFF64748B)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (code == "ALERTAS") Color(0xFFEA580C) else Color(0xFF2563EB),
                    selectedLabelColor = Color.White,
                    containerColor = Color.White,
                    labelColor = Color(0xFF334155)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Color(0xFFE2E8F0),
                    selectedBorderColor = Color(0xFF2563EB)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("chip_category_$code")
            )
        }
    }
}
