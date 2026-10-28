package io.github.deganegane.wortschatz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.deganegane.wortschatz.Bucket
import io.github.deganegane.wortschatz.POS_LABELS
import io.github.deganegane.wortschatz.R
import io.github.deganegane.wortschatz.Word

/** German headword with its article in the gender colour: "das Haus". */
@Composable
fun Headword(
    word: Word,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    val articleColor = LocalArticles.current.of(word.article)
    val text = buildAnnotatedString {
        if (word.article != null && articleColor != null) {
            withStyle(SpanStyle(color = articleColor, fontWeight = FontWeight.Medium)) { append(word.article) }
            append(" ")
        }
        append(word.de)
    }
    Text(text, style = style, modifier = modifier, textAlign = textAlign)
}

/** Grammar line under a headword: part of speech, plural, verb forms. */
@Composable
fun GrammarLine(word: Word, modifier: Modifier = Modifier, center: Boolean = false) {
    val parts = buildList {
        POS_LABELS[word.pos]?.let { add(it) }
        word.cat?.let { add(it) }
        if (word.article != null && word.plural != null) {
            add(if (word.plural == "—") "bez množine" else "mn. die ${word.plural}")
        }
        word.forms?.let { add(it) }
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        textAlign = if (center) TextAlign.Center else null
    )
}

@Composable
fun SpeakButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(painterResource(R.drawable.ic_volume), contentDescription = "Izgovori", modifier = Modifier.size(size * 0.48f))
    }
}

/** The example sentence block: German (with a play button) and its Croatian meaning. */
@Composable
fun ExampleBlock(word: Word, onSpeak: (String) -> Unit, modifier: Modifier = Modifier) {
    if (word.example.isBlank()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    word.example,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    word.exampleHr,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            SpeakButton(onClick = { onSpeak(word.example) }, size = 40.dp)
        }
    }
}

@Composable
fun bucketColor(b: Bucket): Color {
    val a = LocalArticles.current
    val cs = MaterialTheme.colorScheme
    return when (b) {
        Bucket.KNOWN -> a.good
        Bucket.MASTERED -> cs.primary
        Bucket.LEARNING -> cs.secondary
        Bucket.TO_LEARN -> a.bad
        Bucket.UNTESTED, Bucket.ALL -> cs.outline
    }
}

@Composable
fun StatusDot(b: Bucket, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(bucketColor(b)))
}

/** Small rounded label, e.g. "NOVA RIJEČ" or a rank. */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun Legend(items: List<Pair<Bucket, Int>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (b, n) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(b)
                        Spacer(Modifier.width(8.dp))
                        Text(b.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            n.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
