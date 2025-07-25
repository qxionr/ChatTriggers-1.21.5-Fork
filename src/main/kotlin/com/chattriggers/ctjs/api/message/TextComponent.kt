package com.chattriggers.ctjs.api.message

import com.chattriggers.ctjs.CTJS
import com.chattriggers.ctjs.MCEntity
import com.chattriggers.ctjs.api.client.Client
import com.chattriggers.ctjs.api.client.Player
import com.chattriggers.ctjs.api.entity.Entity
import com.chattriggers.ctjs.api.inventory.Item
import com.chattriggers.ctjs.api.inventory.ItemType
import com.chattriggers.ctjs.internal.utils.toIdentifier
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import gg.essential.universal.UChat
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.text.*
import net.minecraft.util.Formatting
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptRuntime
import java.net.URI
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.streams.toList

/**
 * A wrapper around the Minecraft Text class and it's various inheritors.
 *
 * This class acts as a container for "pairs". A pair is a string of text
 * that is associated with a particular [Style]. Unlike Minecraft's Text
 * class, this container is a list, not a tree, which makes them much easier
 * to work with. It implements [List]<[NativeObject]>, so it can be iterated
 * over.
 *
 * Importantly, instances of [TextComponent] are immutable. Methods for
 * "mutation" exist, but they return new instances of [TextComponent]. See
 * [withText] for an example.
 *
 * @see Text
 */
// Note: For the sake of the Text implementations, parts[0] is the "self" part,
//       and parts[1..] are the siblings, but the exposed CT API treats this as
//       a container of individual parts
class TextComponent private constructor(
    private val parts: MutableList<Part>,
    private val chatLineId: Int = -1,
    private val isRecursive: Boolean = false,
) : Text, Iterable<NativeObject> {
    /**
     * Creates an empty [TextComponent] with a single, unstyled, empty part.
     */
    constructor() : this(listOf(Part("", Style.EMPTY)))

    /**
     * Creates a [TextComponent] from a variable number of objects. These
     * objects can be:
     * - A plain string possibly containing formatting codes. If the string has
     *   formatting codes, it will be split into different parts accordingly
     * - A [TextComponent], whose parts will be appended in sequence to this
     *   [TextComponent]'s list of parts
     * - A [Text] object, which acts as a single part
     * - A JS object, which must contain a "text" key, and can optionally contain
     *   any of the [Style] keys:
     *   - color: a [TextColor] or string format of a [TextColor], [Formatting], or a hex value
     *   - bold: boolean
     *   - italic: boolean
     *   - underline: boolean
     *   - strikethrough: boolean
     *   - obfuscated: boolean
     *   - clickEvent: object with { action: [ClickEvent.Action] or string format of a [ClickEvent.Action], value: string or null }
     *   - hoverEvent: object with { action: [HoverEvent.Action], string format of a [HoverEvent.Action], or null, value: string or null }
     *   - insertion: string or null
     *   - font: string format of an [net.minecraft.util.Identifier]
     *
     * @see Style
     */
    constructor(vararg parts: Any) : this(parts.flatMap(Part::of).toMutableList().let {
        if (it.isEmpty()) mutableListOf(Part("", Style.EMPTY)) else it
    })

    /**
     * Returns the text of all parts concatenated without formatting codes.
     */
    val unformattedText by lazy {
        parts.fold("") { prev, curr -> prev + curr.text }
    }

    /**
     * Returns the text of all parts concatenated with formatting codes.
     */
    val formattedText by lazy {
        parts.fold("") { prev, curr -> prev + curr.style_.formatCodes() + curr.text }
    }

    /**
     * Get the chat line ID of this message, if it exists. The chat line can be used
     * to easily edit or delete a message later via [ChatLib.editChat] and
     * [ChatLib.deleteChat].
     *
     * @return the chat line ID of the message, or -1 if this [TextComponent] does
     *         not have an associated chat line ID.
     */
    fun getChatLineId() = chatLineId

    /**
     * @return a new [TextComponent] with the given chat line id
     */
    @JvmOverloads
    fun withChatLineId(id: Int = ThreadLocalRandom.current().nextInt()) = copy(chatLineId = id)

    /**
     * If this [TextComponent] is recursive, sending this instance (via [chat] or
     * [actionBar]) may trigger other `chat` triggers as if it had been received by
     * the server. [TextComponent]s are non-recursive by default.
     *
     * @return true if the message can trigger other triggers.
     */
    fun isRecursive(): Boolean = isRecursive

    /**
     * Sets whether the message can trigger other triggers.
     *
     * @param recursive true if message can trigger other triggers.
     */
    @JvmOverloads
    fun withRecursive(recursive: Boolean = true) = copy(isRecursive = recursive)

    /**
     * @return a new [TextComponent] with the specified [value] appended to the end.
     *         This accepts all types of objects that the vararg constructor does.
     */
    fun withText(value: Any) = copy(parts = (parts + Part.of(value)).toMutableList())

    /**
     * @return a new [TextComponent] with the specified [value] inserted at [index].
     *         This accepts all types of objects that the vararg constructor does.
     */
    fun withTextAt(index: Int, value: Any) =
        copy(parts = (parts.take(index) + Part.of(value) + parts.drop(index)).toMutableList())

    /**
     * @return a new [TextComponent] without the part at [index]
     */
    fun withoutTextAt(index: Int) =
        copy(parts = (parts.take(index) + parts.drop(index + 1)).toMutableList())

    /**
     * Edits this text component, replacing it with the given [newText]. Note that
     * this compares [TextComponent]s based on [formattedText]; if an exact match
     * is needed, use [ChatLib.editChat] in conjunction with a chat line ID.
     */
    fun edit(newText: TextComponent) = apply {
        ChatLib.editChat(this, newText)
    }

    /**
     * Edits this text component, replacing it with a new [TextComponent] from the
     * given [parts]. Note that this compares [TextComponent]s based on
     * [formattedText]; if an exact match is needed, use [ChatLib.editChat] in
     * conjunction with a chat line ID.
     */
    fun edit(vararg parts: Any) = edit(TextComponent(*parts))

    /**
     * Deletes this text component. Note that this compares [TextComponent]s based on
     * [formattedText]; if an exact match is needed, use [ChatLib.editChat] in conjunction
     * with a chat line ID.
     */
    fun delete() = apply {
        ChatLib.deleteChat(this)
    }

    /**
     * Sends this [TextComponent] to the players chat.
     *
     * Note that this is purely client-side, and will not be sent to the server. If [isRecursive],
     * will trigger any matching `chat` triggers
     *
     * @see ChatLib.chat
     * @see ChatLib.say
     */
    fun chat() = apply {
        if (Player.toMC() == null)
            return@apply

        if (chatLineId != -1) {
            ChatLib.sendMessageWithId(this)
            return@apply
        }

        if (isRecursive) {
            Client.scheduleTask {
                Client.getMinecraft().networkHandler?.onGameMessage(GameMessageS2CPacket(this, false))
            }
        } else {
            Player.toMC()?.sendMessage(this, false)
        }
    }

    /**
     * Sends this [TextComponent] to the players action bar.
     *
     * If [isRecursive], will trigger any matching `actionBar` triggers
     *
     * @see ChatLib.actionBar
     */
    fun actionBar() = apply {
        if (Player.toMC() == null)
            return@apply

        if (isRecursive) {
            Client.scheduleTask {
                Client.getMinecraft().networkHandler?.onGameMessage(GameMessageS2CPacket(this, true))
            }
        } else {
            Player.toMC()?.sendMessage(this, true)
        }
    }

    override fun toString() = formattedText

    internal fun toMutableText() = Text.empty().apply {
        parts.forEach(::append)
    }

    // Make this method manually to avoid exposing it as a public API
    private fun copy(
        parts: MutableList<Part> = this.parts,
        chatLineId: Int = this.chatLineId,
        isRecursive: Boolean = this.isRecursive
    ) = TextComponent(parts, chatLineId, isRecursive)

    //////////
    // Text //
    //////////

    override fun getContent(): TextContent = parts[0].content

    override fun getString(): String = parts[0].text

    override fun getStyle(): Style = parts[0].style_

    override fun getSiblings(): MutableList<Text> = parts.drop(1).toMutableList()

    override fun asOrderedText(): OrderedText = OrderedText { visitor ->
        var i = 0
        parts.all {
            it.text.codePoints().toList().all { cp ->
                visitor.accept(i++, it.style, cp)
            }
        }
    }

    // "List<NativeObject>" impl
    // This interface doesn't actually implement List<NativeObject>, as that would cause Rhino to convert it to a JS
    // array when returned from a Java API
    val size by parts::size

    operator fun contains(element: NativeObject) = parts.any { ScriptRuntime.eq(element, it.nativeObject) }

    fun containsAll(elements: Collection<NativeObject>) = elements.all(::contains)

    operator fun get(index: Int) = parts[index].nativeObject

    fun indexOf(element: NativeObject) = parts.indexOfFirst { it.nativeObject == element }

    fun isEmpty() = parts.isEmpty()

    override fun iterator() = parts.map(Part::nativeObject).iterator()

    private class Part(val content: PartContent) : Text {
        val text by content::text
        val style_ by content::style_

        val nativeObject: NativeObject by lazy {
            val cx = Context.getContext()
            cx.newObject(cx.topCallScope).also {
                it.put("text", it, text)
                if (style_.color != null)
                    it.put("color", it, style_.color)
                if (style_.isBold)
                    it.put("bold", it, true)
                if (style_.isItalic)
                    it.put("italic", it, true)
                if (style_.isUnderlined)
                    it.put("underline", it, true)
                if (style_.isStrikethrough)
                    it.put("strikethrough", it, true)
                if (style_.isObfuscated)
                    it.put("obfuscated", it, true)
                style_.clickEvent?.let { event ->
                    if (event.action != null) {
                        val clickEvent = NativeObject()
                        clickEvent.put("action", clickEvent, event.action.asString())
                        clickEvent.put("value", clickEvent, getEventValue(event))

                        it.put("clickEvent", it, clickEvent)
                    }
                }
                style_.hoverEvent?.let { event ->
                    if (event.action != null) {
                        val hoverEvent = NativeObject()
                        hoverEvent.put("action", hoverEvent, event.action.asString())
                        hoverEvent.put("value", hoverEvent, getEventValue(event))

                        it.put("hoverEvent", it, hoverEvent)
                    }
                }
                if (style_.insertion != null)
                    it.put("insertion", it, style_.insertion)
                if (style_.font != null && style_.font.toString() != "minecraft:default")
                    it.put("font", it, style_.font)
            }
        }

        constructor(text: String, style: Style) : this(PartContent(text, style))

        override fun getContent(): TextContent = content

        override fun getString(): String = text

        override fun getStyle(): Style = style_

        override fun getSiblings(): MutableList<Text> = mutableListOf()

        override fun asTruncatedString(length: Int): String = text.take(length)

        override fun asOrderedText(): OrderedText = OrderedText { visitor ->
            text.codePoints().toList().withIndex().all { (index, cp) ->
                visitor.accept(index, style_, cp)
            }
        }

        companion object {
            fun of(obj: Any?): List<Part> = when (obj) {
                is NativeObject -> {
                    val text = obj["text"]
                        ?: throw IllegalArgumentException("Expected TextComponent part to have a \"text\" key")
                    require(text is CharSequence) { "TextComponent part's \"text\" key must be a string" }
                    listOf(Part(ChatLib.addColor(text.toString()), jsObjectToStyle(obj)))
                }
                is Part -> listOf(obj)
                is TextComponent -> obj.parts
                is Text -> {
                    val parts = mutableListOf<Part>()

                    obj.content.visit({ style, text ->
                        parts.add(Part(text, style))
                        Optional.empty<Any>()
                    }, obj.style)

                    parts + obj.siblings.flatMap(::of)
                }
                is CharSequence -> {
                    val parts = mutableListOf<Part>()
                    val builder = StringBuilder()
                    var lastStyle = Style.EMPTY

                    TextVisitFactory.visitFormatted(ChatLib.addColor(obj.toString()), 0, Style.EMPTY) { _, style, cp ->
                        if (style != lastStyle) {
                            parts.add(Part(builder.toString(), lastStyle))
                            lastStyle = style
                            builder.clear()
                        }
                        builder.appendCodePoint(cp)
                        true
                    }

                    if (builder.isNotEmpty())
                        parts.add(Part(builder.toString(), lastStyle))

                    parts
                }
                is List<*> -> obj.flatMap(::of)
                null -> emptyList()
                else -> throw IllegalArgumentException("Cannot convert ${obj::class.simpleName} to TextComponent part")
            }
        }
    }

    // Must be a separate class since Text and TextContent have an identical "visit" method which fails loom remapping
    private class PartContent(val text: String, val style_: Style) : TextContent {
        override fun <T : Any?> visit(visitor: StringVisitable.Visitor<T>): Optional<T> = visitor.accept(text)

        override fun <T> visit(visitor: StringVisitable.StyledVisitor<T>, style: Style): Optional<T> {
            return visitor.accept(this.style_.withParent(style), text)
        }

        override fun getType(): TextContent.Type<*> = TextContent.Type(CODEC, "${CTJS.MOD_ID}_part")

        companion object {
            private val CODEC: MapCodec<PartContent> = RecordCodecBuilder.mapCodec { builder ->
                builder.group(
                    Codec.STRING.fieldOf("text").forGetter(PartContent::text),
                    net.minecraft.text.Style.Codecs.CODEC.fieldOf("style").forGetter(PartContent::style_),
                ).apply(builder) { text, style -> PartContent(text, style) }
            }
        }
    }

    internal companion object {
        private val colorToFormatChar = Formatting.entries.mapNotNull { format ->
            TextColor.fromFormatting(format)?.let { it to format }
        }.toMap()

        internal fun jsObjectToStyle(obj: NativeObject): Style {
            return Style.EMPTY
                .withColor(obj["color"]?.let { color ->
                    when (color) {
                        is TextColor -> color
                        is Formatting -> TextColor.fromFormatting(color)
                        is Number -> TextColor.fromRgb(color.toInt())
                        is CharSequence -> TextColor.parse(color.toString()).result().orElseThrow {
                            IllegalArgumentException("Could not parse \"$color\" as a text color")
                        }
                        else -> throw IllegalArgumentException("Could not convert type ${color::class.simpleName} to a text color")
                    }
                })
                .withBold(
                    obj.getOrDefault("bold", false) as? Boolean
                        ?: error("Expected \"bold\" key to be a boolean")
                )
                .withItalic(
                    obj.getOrDefault("italic", false) as? Boolean
                        ?: error("Expected \"italic\" key to be a boolean")
                )
                .withUnderline(
                    obj.getOrDefault("underline", false) as? Boolean
                        ?: error("Expected \"underline\" key to be a boolean")
                )
                .withStrikethrough(
                    obj.getOrDefault("strikethrough", false) as? Boolean
                        ?: error("Expected \"strikethrough\" key to be a boolean")
                )
                .withObfuscated(
                    obj.getOrDefault("obfuscated", false) as? Boolean
                        ?: error("Expected \"obfuscated\" key to be a boolean")
                )
                .withClickEvent(makeClickEvent(obj["clickEvent"]))
                .withHoverEvent(makeHoverEvent(obj["hoverEvent"]))
                .withInsertion(
                    when (val insertion = obj["insertion"]) {
                        null -> null
                        is CharSequence -> insertion.toString()
                        else -> error("Expected \"insertion\" key to be a String")
                    }
                )
                .withFont(
                    when (val font = obj["font"]) {
                        null -> null
                        is CharSequence -> font.toString().toIdentifier()
                        else -> error("Expected \"font\" key to be a String")
                    }
                )
        }

        private fun Style.formatCodes() = buildString {
            append("§r")

            when {
                isBold -> append("§l")
                isItalic -> append("§o")
                isUnderlined -> append("§n")
                isStrikethrough -> append("§m")
                isObfuscated -> append("§k")
            }

            color?.let(colorToFormatChar::get)?.run(::append)
        }

        private fun makeClickEvent(clickEvent: Any?): ClickEvent? {
            if (clickEvent is ClickEvent?)
                return clickEvent

            require(clickEvent is NativeObject) { "Expected \"clickEvent\" key to be an object or ClickEvent" }

            val action = clickEvent["action"]
            val value = clickEvent["value"]

            val clickAction = when (action) {
                is ClickEvent.Action -> action
                is CharSequence -> ClickEvent.Action.valueOf(action.toString().uppercase())
                null -> if (value != null) {
                    error("Cannot set Style's click value without a click action")
                } else return null
                else -> error("Style.withClickAction() expects a String, ClickEvent.Action, or null, but got ${action::class.simpleName}")
            }

            val clickValue = when (value) {
                null -> null
                is CharSequence -> value.toString().let {
                    if (clickAction == ClickEvent.Action.OPEN_URL) {
                        if (!it.startsWith("http://") && !it.startsWith("https://")) {
                            return@let "http://$it"
                        }
                    }
                    it
                }
                else -> error("Expected \"value\" key to be a string")
            }.orEmpty()

            return when (clickAction) {
                ClickEvent.Action.OPEN_URL -> ClickEvent.OpenUrl(URI(clickValue))
                ClickEvent.Action.OPEN_FILE -> ClickEvent.OpenFile(clickValue)
                ClickEvent.Action.RUN_COMMAND -> ClickEvent.RunCommand(clickValue)
                ClickEvent.Action.SUGGEST_COMMAND -> ClickEvent.SuggestCommand(clickValue)
                ClickEvent.Action.CHANGE_PAGE -> clickValue.toIntOrNull()?. let { ClickEvent.ChangePage(it) }
                ClickEvent.Action.COPY_TO_CLIPBOARD -> ClickEvent.CopyToClipboard(clickValue)
            }
        }

        private fun makeHoverEvent(hoverEvent: Any?): HoverEvent? {
            if (hoverEvent is HoverEvent?)
                return hoverEvent

            require(hoverEvent is NativeObject) { "Expected \"hoverEvent\" key to be an object or HoverEvent" }

            val action = hoverEvent["action"]
            val value = hoverEvent["value"]

            val hoverAction = when (action) {
                is HoverEvent.Action -> action
                is CharSequence -> when (action.toString().uppercase()) {
                    "SHOW_TEXT" -> HoverEvent.Action.SHOW_TEXT
                    "SHOW_ITEM" -> HoverEvent.Action.SHOW_ITEM
                    "SHOW_ENTITY" -> HoverEvent.Action.SHOW_ENTITY
                    else -> throw IllegalStateException("Unknown hover event action \"$action\"")
                }
                null -> if (value != null) {
                    error("Cannot set Style's hover value without a hover action")
                } else return null
                else -> error("Style.withHoverAction() expects a String, HoverEvent.Action, or null, but got ${action::class.simpleName}")
            }

            if (value == null)
                return null

            return when (hoverAction) {
                HoverEvent.Action.SHOW_TEXT -> HoverEvent.ShowText(TextComponent(value))
                HoverEvent.Action.SHOW_ITEM -> HoverEvent.ShowItem(parseItemContent(value).item)
                HoverEvent.Action.SHOW_ENTITY -> HoverEvent.ShowEntity(parseEntityContent(value))
            }
        }

        private fun getEventValue(event: HoverEvent): Any {
            return when (event) {
                is HoverEvent.ShowItem -> event.item
                is HoverEvent.ShowText -> event.value
                is HoverEvent.ShowEntity -> event.entity
                else -> error("${event::class} is not of a supported type")
            }
        }

        private fun getEventValue(event: ClickEvent): Any {
            return when (event) {
                is ClickEvent.OpenUrl -> event.uri
                is ClickEvent.OpenFile -> event.file()
                is ClickEvent.RunCommand -> event.command
                is ClickEvent.SuggestCommand -> event.command
                is ClickEvent.ChangePage -> event.page
                is ClickEvent.CopyToClipboard -> event.value
                else -> error("${event::class} is not of a supported type")
            }
        }

        private fun parseItemContent(obj: Any): HoverEvent.ShowItem {
            return when (obj) {
                is ItemStack -> obj
                is Item -> obj.toMC()
                is CharSequence -> ItemType(obj.toString()).asItem().toMC()
                is HoverEvent.ShowItem -> return obj
                else -> error("${obj::class} cannot be parsed as an item HoverEvent")
            }.let(HoverEvent::ShowItem)
        }

        private fun parseEntityContent(obj: Any): HoverEvent.EntityContent? {
            return when (obj) {
                is MCEntity -> obj
                is Entity -> obj.toMC()
                is HoverEvent.EntityContent -> return obj
                else -> error("${obj::class} cannot be parsed as an entity HoverEvent")
            }.let { HoverEvent.EntityContent(it.type, it.uuid, it.name) }
        }
    }
}
